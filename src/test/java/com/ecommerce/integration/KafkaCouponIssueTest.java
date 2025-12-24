package com.ecommerce.integration;

import com.ecommerce.application.event.CouponIssueEvent;
import com.ecommerce.config.KafkaConfig;
import com.ecommerce.config.KafkaIntegrationTestSupport;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.ecommerce.domain.service.CouponIssueResult;
import com.ecommerce.infrastructure.kafka.KafkaCouponIssueService;
import com.ecommerce.infrastructure.kafka.consumer.CouponKafkaConsumer;
import com.ecommerce.infrastructure.redis.CouponRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class KafkaCouponIssueTest extends KafkaIntegrationTestSupport {

    @Autowired
    private KafkaCouponIssueService kafkaCouponIssueService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponRedisRepository couponRedisRepository;

    @Autowired
    private CouponKafkaConsumer couponKafkaConsumer;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        couponRedisRepository.clearQueue();
        couponKafkaConsumer.resetDltCount();
    }

    @Test
    @DisplayName("[Kafka] 쿠폰 발급 요청 시 Kafka를 통해 DB에 저장된다")
    void issueCoupon_shouldSaveToDbViaKafka() {
        // given
        testCoupon = createCoupon("테스트 쿠폰", 100);
        initializeCoupon(testCoupon);
        Long userId = 1L;

        // when
        CouponIssueResult result = kafkaCouponIssueService.issue(userId, testCoupon.getId());

        // then - 즉시 SUCCESS 응답
        assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);

        // Kafka Consumer가 처리할 때까지 대기
        Long couponId = testCoupon.getId();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = userCouponRepository.findAll().stream()
                    .filter(uc -> uc.getCouponId().equals(couponId) && uc.getUserId().equals(userId))
                    .count();
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("[Kafka] 동일 사용자 중복 발급 요청 시 ALREADY_ISSUED 반환")
    void issueCoupon_duplicateRequest_shouldReturnAlreadyIssued() {
        // given
        testCoupon = createCoupon("중복 테스트 쿠폰", 100);
        initializeCoupon(testCoupon);
        Long userId = 1L;

        // when
        CouponIssueResult firstResult = kafkaCouponIssueService.issue(userId, testCoupon.getId());
        CouponIssueResult secondResult = kafkaCouponIssueService.issue(userId, testCoupon.getId());

        // then
        assertThat(firstResult).isEqualTo(CouponIssueResult.SUCCESS);
        assertThat(secondResult).isEqualTo(CouponIssueResult.ALREADY_ISSUED);
    }

    @Test
    @DisplayName("[Kafka] 수량 초과 시 SOLD_OUT 반환")
    void issueCoupon_exceedQuantity_shouldReturnSoldOut() {
        // given
        testCoupon = createCoupon("한정 쿠폰", 2);
        initializeCoupon(testCoupon);

        // when
        CouponIssueResult result1 = kafkaCouponIssueService.issue(1L, testCoupon.getId());
        CouponIssueResult result2 = kafkaCouponIssueService.issue(2L, testCoupon.getId());
        CouponIssueResult result3 = kafkaCouponIssueService.issue(3L, testCoupon.getId());

        // then
        assertThat(result1).isEqualTo(CouponIssueResult.SUCCESS);
        assertThat(result2).isEqualTo(CouponIssueResult.SUCCESS);
        assertThat(result3).isEqualTo(CouponIssueResult.SOLD_OUT);
    }

    @Test
    @DisplayName("[Kafka] 동시 100명 요청, 10개 한정 쿠폰 - 정확히 10명만 발급")
    void issueCoupon_concurrent100Users_10Coupons() throws InterruptedException {
        // given
        int totalUsers = 100;
        int couponLimit = 10;

        testCoupon = createCoupon("선착순 10명 쿠폰", couponLimit);
        initializeCoupon(testCoupon);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CouponIssueResult result = kafkaCouponIssueService.issue(userId, testCoupon.getId());
                    if (result == CouponIssueResult.SUCCESS) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then - Redis에서 정확히 10명만 SUCCESS
        assertThat(successCount.get()).isEqualTo(couponLimit);

        // Kafka Consumer가 DB에 저장할 때까지 대기
        Long couponId = testCoupon.getId();
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long actualIssued = userCouponRepository.findAll().stream()
                    .filter(uc -> uc.getCouponId().equals(couponId))
                    .count();
            assertThat(actualIssued).isEqualTo(couponLimit);
        });
    }

    @Test
    @DisplayName("[Kafka DLT] 처리 실패 시 재시도 후 DLT로 이동")
    void consumeFail_shouldRetryAndMoveToDlt() {
        // given
        testCoupon = createCoupon("DLT 테스트 쿠폰", 100);

        // userId가 음수면 Consumer에서 강제로 예외 발생
        CouponIssueEvent failEvent = new CouponIssueEvent(testCoupon.getId(), -1L);

        // when - 직접 Kafka로 발행 (Redis 체크 우회)
        kafkaTemplate.send(KafkaConfig.TOPIC_COUPON_ISSUE, testCoupon.getId().toString(), failEvent);

        // then - 재시도 3회 (1초 + 2초 + 4초) + 여유 시간 후 DLT 처리 확인
        // @RetryableTopic attempts=3 → 최초 시도 + 2회 재시도 = 총 3회 시도
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(couponKafkaConsumer.getDltCount()).isEqualTo(1);
                });
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

    private void initializeCoupon(Coupon coupon) {
        couponRedisRepository.initializeCoupon(coupon.getId());
        couponRedisRepository.cacheCouponInfo(
                coupon.getId(),
                coupon.getMaxIssueCount(),
                coupon.getIssueStartDate(),
                coupon.getIssueEndDate()
        );
    }
}
