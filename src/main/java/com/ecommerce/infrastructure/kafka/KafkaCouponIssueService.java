package com.ecommerce.infrastructure.kafka;

import com.ecommerce.application.event.CouponIssueEvent;
import com.ecommerce.config.KafkaConfig;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.service.CouponIssueResult;
import com.ecommerce.domain.service.CouponIssuer;
import com.ecommerce.infrastructure.redis.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka 기반 비동기 쿠폰 발급 서비스
 *
 * 구조: Redis Set (동시성 제어) + Kafka (비동기 처리)
 * - Redis Set: 중복 발급 방지 + 수량 체크
 * - Kafka: couponId를 키로 발행하여 같은 쿠폰 요청은 같은 파티션에서 순차 처리
 */
@Slf4j
@Service("kafkaCouponIssueService")
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaCouponIssueService implements CouponIssuer {

    private final CouponRedisRepository couponRedisRepository;
    private final CouponRepository couponRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public CouponIssueResult issue(Long userId, Long couponId) {
        CouponRedisRepository.CouponInfo cachedInfo = couponRedisRepository.getCouponInfo(couponId);

        if (cachedInfo != null) {
            if (!cachedInfo.canIssue()) {
                return CouponIssueResult.SOLD_OUT;
            }
            return tryIssueWithKafka(userId, couponId, cachedInfo.maxQuantity());
        }

        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            return CouponIssueResult.INVALID_COUPON;
        }

        if (!coupon.canIssue()) {
            return CouponIssueResult.SOLD_OUT;
        }

        couponRedisRepository.cacheCouponInfo(
                couponId,
                coupon.getMaxIssueCount(),
                coupon.getIssueStartDate(),
                coupon.getIssueEndDate()
        );

        return tryIssueWithKafka(userId, couponId, coupon.getMaxIssueCount());
    }

    private CouponIssueResult tryIssueWithKafka(Long userId, Long couponId, int maxQuantity) {
        // Redis Set에 추가하여 동시성 제어 (Queue push 없이)
        CouponIssueResult redisResult = couponRedisRepository.tryIssue(userId, couponId, maxQuantity, false);
        if (redisResult != CouponIssueResult.SUCCESS) {
            return redisResult;
        }

        // Kafka로 발행 (couponId를 키로 사용하여 같은 쿠폰은 같은 파티션으로)
        CouponIssueEvent event = new CouponIssueEvent(couponId, userId);
        kafkaTemplate.send(KafkaConfig.TOPIC_COUPON_ISSUE, couponId.toString(), event);

        log.debug("Kafka 쿠폰 발급 이벤트 발행: couponId={}, userId={}", couponId, userId);
        return CouponIssueResult.SUCCESS;
    }

    @Override
    public boolean isIssued(Long userId, Long couponId) {
        return couponRedisRepository.isIssued(userId, couponId);
    }

    public long getIssuedCount(Long couponId) {
        return couponRedisRepository.getIssuedCount(couponId);
    }

    public void initializeCoupon(Long couponId) {
        couponRedisRepository.initializeCoupon(couponId);
    }
}
