package com.ecommerce.application.scheduler;

import com.ecommerce.config.KafkaIntegrationTestSupport;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.FailedEventRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DLT 재처리 스케줄러 테스트")
class DltRetrySchedulerTest extends KafkaIntegrationTestSupport {

    @Autowired
    private DltRetryScheduler dltRetryScheduler;

    @Autowired
    private FailedEventRepository failedEventRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        failedEventRepository.deleteAll();

        testCoupon = createCoupon("DLT 테스트 쿠폰", 100);
    }

    @Test
    @DisplayName("PENDING 상태의 실패 이벤트가 재처리되어 RECOVERED로 변경된다")
    void pendingFailedEvent_shouldBeRecovered() throws Exception {
        // given
        String payload = objectMapper.writeValueAsString(
                new com.ecommerce.application.event.CouponIssueEvent(testCoupon.getId(), 1L)
        );
        FailedEvent failedEvent = new FailedEvent(
                "coupon-issue",
                testCoupon.getId().toString(),
                payload,
                "테스트 실패"
        );
        failedEvent.setNextRetryAtForTest(LocalDateTime.now().minusSeconds(1)); // 즉시 재시도 가능
        failedEventRepository.save(failedEvent);

        // when
        dltRetryScheduler.retryFailedEvents();

        // then
        List<FailedEvent> recoveredEvents = failedEventRepository.findByStatus(FailedEventStatus.RECOVERED);
        assertThat(recoveredEvents).hasSize(1);

        // 쿠폰도 실제로 발급되었는지 확인
        assertThat(userCouponRepository.findByUserIdAndCouponId(1L, testCoupon.getId())).isPresent();
    }

    @Test
    @DisplayName("이미 발급된 쿠폰은 재처리 시 RECOVERED로 처리된다 (멱등성)")
    void alreadyIssuedCoupon_shouldBeRecovered() throws Exception {
        // given - 이미 발급된 상태
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        userCouponRepository.save(new UserCoupon(1L, testCoupon.getId(), expiresAt));

        String payload = objectMapper.writeValueAsString(
                new com.ecommerce.application.event.CouponIssueEvent(testCoupon.getId(), 1L)
        );
        FailedEvent failedEvent = new FailedEvent(
                "coupon-issue",
                testCoupon.getId().toString(),
                payload,
                "테스트 실패"
        );
        failedEvent.setNextRetryAtForTest(LocalDateTime.now().minusSeconds(1)); // 즉시 재시도 가능
        failedEventRepository.save(failedEvent);

        // when
        dltRetryScheduler.retryFailedEvents();

        // then - 이미 발급됐으므로 성공(RECOVERED)으로 처리
        List<FailedEvent> recoveredEvents = failedEventRepository.findByStatus(FailedEventStatus.RECOVERED);
        assertThat(recoveredEvents).hasSize(1);
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 ABANDONED로 변경된다")
    void maxRetryExceeded_shouldBeAbandoned() throws Exception {
        // given - 음수 userId는 항상 실패
        String payload = objectMapper.writeValueAsString(
                new com.ecommerce.application.event.CouponIssueEvent(testCoupon.getId(), -1L)
        );
        FailedEvent failedEvent = new FailedEvent(
                "coupon-issue",
                testCoupon.getId().toString(),
                payload,
                "테스트 실패"
        );
        failedEvent.setNextRetryAtForTest(LocalDateTime.now().minusSeconds(1)); // 즉시 재시도 가능
        failedEventRepository.save(failedEvent);

        // when - 4번 재시도 (maxRetryCount=3)
        // 각 재시도 후 nextRetryAt을 과거로 설정해야 다음 재시도 가능
        for (int i = 0; i < 4; i++) {
            dltRetryScheduler.retryFailedEvents();
            // 아직 ABANDONED가 아니면 nextRetryAt을 과거로 설정
            List<FailedEvent> pending = failedEventRepository.findByStatus(FailedEventStatus.PENDING);
            for (FailedEvent e : pending) {
                e.setNextRetryAtForTest(LocalDateTime.now().minusSeconds(1));
                failedEventRepository.save(e);
            }
        }

        // then
        List<FailedEvent> abandonedEvents = failedEventRepository.findByStatus(FailedEventStatus.ABANDONED);
        assertThat(abandonedEvents).hasSize(1);
        assertThat(abandonedEvents.get(0).getRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 재처리 실패한다")
    void nonExistentCoupon_shouldFail() throws Exception {
        // given
        String payload = objectMapper.writeValueAsString(
                new com.ecommerce.application.event.CouponIssueEvent(99999L, 1L)
        );
        FailedEvent failedEvent = new FailedEvent(
                "coupon-issue",
                "99999",
                payload,
                "테스트 실패"
        );
        failedEvent.setNextRetryAtForTest(LocalDateTime.now().minusSeconds(1)); // 즉시 재시도 가능
        failedEventRepository.save(failedEvent);

        // when
        dltRetryScheduler.retryFailedEvents();

        // then - 실패 상태 유지
        List<FailedEvent> pendingEvents = failedEventRepository.findByStatus(FailedEventStatus.PENDING);
        assertThat(pendingEvents).hasSize(1);
        assertThat(pendingEvents.get(0).getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 실패 이벤트를 한 번에 처리한다")
    void multipleFailedEvents_shouldBeProcessed() throws Exception {
        // given
        for (long userId = 1; userId <= 3; userId++) {
            String payload = objectMapper.writeValueAsString(
                    new com.ecommerce.application.event.CouponIssueEvent(testCoupon.getId(), userId)
            );
            FailedEvent failedEvent = new FailedEvent(
                    "coupon-issue",
                    testCoupon.getId().toString(),
                    payload,
                    "테스트 실패"
            );
            failedEvent.setNextRetryAtForTest(LocalDateTime.now().minusSeconds(1)); // 즉시 재시도 가능
            failedEventRepository.save(failedEvent);
        }

        // when
        dltRetryScheduler.retryFailedEvents();

        // then
        List<FailedEvent> recoveredEvents = failedEventRepository.findByStatus(FailedEventStatus.RECOVERED);
        assertThat(recoveredEvents).hasSize(3);

        // 쿠폰도 3개 발급되었는지 확인
        assertThat(userCouponRepository.findAll().stream()
                .filter(uc -> uc.getCouponId().equals(testCoupon.getId()))
                .count()).isEqualTo(3);
    }

    @Test
    @DisplayName("쿠폰이 소진된 경우 RECOVERED로 처리된다")
    void soldOutCoupon_shouldBeRecovered() throws Exception {
        // given - 수량 1개짜리 쿠폰을 만들고 발급해서 소진시킴
        Coupon soldOutCoupon = createCoupon("소진 쿠폰", 1);
        soldOutCoupon.issue(); // 1개 발급 → 소진
        couponRepository.save(soldOutCoupon);

        String payload = objectMapper.writeValueAsString(
                new com.ecommerce.application.event.CouponIssueEvent(soldOutCoupon.getId(), 1L)
        );
        FailedEvent failedEvent = new FailedEvent(
                "coupon-issue",
                soldOutCoupon.getId().toString(),
                payload,
                "테스트 실패"
        );
        failedEvent.setNextRetryAtForTest(LocalDateTime.now().minusSeconds(1)); // 즉시 재시도 가능
        failedEventRepository.save(failedEvent);

        // when
        dltRetryScheduler.retryFailedEvents();

        // then - 소진된 경우도 성공으로 처리 (더 이상 재시도 불필요)
        List<FailedEvent> recoveredEvents = failedEventRepository.findByStatus(FailedEventStatus.RECOVERED);
        assertThat(recoveredEvents).hasSize(1);
    }

    @Test
    @DisplayName("지수 백오프: 재시도 시간이 안 된 이벤트는 처리하지 않는다")
    void exponentialBackoff_shouldNotProcessBeforeNextRetryAt() throws Exception {
        // given - nextRetryAt이 미래인 이벤트 (직접 설정)
        String payload = objectMapper.writeValueAsString(
                new com.ecommerce.application.event.CouponIssueEvent(testCoupon.getId(), 1L)
        );
        FailedEvent failedEvent = new FailedEvent(
                "coupon-issue",
                testCoupon.getId().toString(),
                payload,
                "테스트 실패"
        );
        // 생성자에서 nextRetryAt이 30초 후로 설정됨
        failedEventRepository.save(failedEvent);

        // when - 즉시 실행 (아직 30초 안 지남)
        dltRetryScheduler.retryFailedEvents();

        // then - 아직 처리 안 됨
        List<FailedEvent> pendingEvents = failedEventRepository.findByStatus(FailedEventStatus.PENDING);
        assertThat(pendingEvents).hasSize(1);
        assertThat(pendingEvents.get(0).getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("지수 백오프: nextRetryAt 값이 지수적으로 증가한다")
    void exponentialBackoff_nextRetryAtShouldIncreaseExponentially() throws Exception {
        // given
        String payload = objectMapper.writeValueAsString(
                new com.ecommerce.application.event.CouponIssueEvent(testCoupon.getId(), -1L)  // 항상 실패
        );
        FailedEvent event = new FailedEvent(
                "coupon-issue",
                testCoupon.getId().toString(),
                payload,
                "테스트"
        );

        // when - 첫 실패 후 markAsFailed 호출
        event.retry();
        event.markAsFailed("첫 번째 실패");
        LocalDateTime firstNextRetry = event.getNextRetryAt();

        // 두 번째 실패
        event.retry();
        event.markAsFailed("두 번째 실패");
        LocalDateTime secondNextRetry = event.getNextRetryAt();

        // then - 두 번째 재시도 시간이 첫 번째보다 길어야 함
        // 첫 번째: 30초 * 2^1 = 60초
        // 두 번째: 30초 * 2^2 = 120초
        assertThat(secondNextRetry).isAfter(firstNextRetry);

        // 대략적인 간격 확인 (30초 → 60초 → 120초)
        long firstDelaySeconds = java.time.Duration.between(LocalDateTime.now(), firstNextRetry).getSeconds();
        long secondDelaySeconds = java.time.Duration.between(LocalDateTime.now(), secondNextRetry).getSeconds();

        assertThat(secondDelaySeconds).isGreaterThan(firstDelaySeconds);
    }

    private Coupon createCoupon(String name, int maxCount) {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                name,
                DiscountType.PERCENTAGE,
                10,
                maxCount,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        return couponRepository.save(coupon);
    }
}
