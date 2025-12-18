package com.ecommerce.infrastructure.kafka.consumer;

import com.ecommerce.application.event.CouponIssueEvent;
import com.ecommerce.config.KafkaConfig;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 쿠폰 발급 Kafka Consumer
 *
 * couponId를 키로 사용하므로 같은 쿠폰 요청은 같은 파티션에서 순차 처리됨.
 * 이를 통해 DB 저장 시 정확한 수량 제어 가능.
 */
@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class CouponKafkaConsumer {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    // DLT 처리 횟수 (테스트용)
    private final java.util.concurrent.atomic.AtomicInteger dltCount = new java.util.concurrent.atomic.AtomicInteger(0);

    public int getDltCount() {
        return dltCount.get();
    }

    public void resetDltCount() {
        dltCount.set(0);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
            topics = KafkaConfig.TOPIC_COUPON_ISSUE,
            groupId = "coupon-issue-service"
    )
    @Transactional
    public void consume(CouponIssueEvent event) {
        Long couponId = event.couponId();
        Long userId = event.userId();

        log.debug("Kafka 쿠폰 발급 처리 시작: couponId={}, userId={}", couponId, userId);

        // 테스트용: userId가 음수면 강제로 예외 발생 (DLT 테스트)
        if (userId < 0) {
            throw new IllegalArgumentException("DLT 테스트용 강제 실패: userId=" + userId);
        }

        // 1. 이미 발급되었는지 확인 (멱등성)
        Optional<UserCoupon> existing = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existing.isPresent()) {
            log.debug("이미 발급된 쿠폰: couponId={}, userId={}", couponId, userId);
            return;
        }

        // 2. 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            log.warn("쿠폰을 찾을 수 없음: couponId={}", couponId);
            return;
        }

        // 3. 수량 체크 (최종 검증선)
        if (coupon.getCurrentIssueCount() >= coupon.getMaxIssueCount()) {
            log.debug("쿠폰 소진: couponId={}, current={}, max={}",
                    couponId, coupon.getCurrentIssueCount(), coupon.getMaxIssueCount());
            return;
        }

        // 4. UserCoupon 저장
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
        UserCoupon userCoupon = new UserCoupon(userId, couponId, expiresAt);
        userCouponRepository.save(userCoupon);

        // 5. 발급 카운트 증가
        coupon.issue();
        couponRepository.save(coupon);

        log.debug("Kafka 쿠폰 발급 완료: couponId={}, userId={}", couponId, userId);
    }

    @DltHandler
    public void handleDlt(CouponIssueEvent event) {
        log.error("[DLT] 쿠폰 발급 최종 실패 - couponId={}, userId={} | 수동 처리 필요",
                event.couponId(), event.userId());
        dltCount.incrementAndGet();
        // TODO: Slack 알림 연동
    }
}
