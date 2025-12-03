package com.ecommerce.infrastructure.redis;

import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.service.CouponIssuer;
import com.ecommerce.domain.service.CouponIssueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Redis 기반 비동기 쿠폰 발급 서비스
 *
 * 처리 흐름:
 * 1. 요청 즉시: Redis Set으로 중복/수량 체크 → 대기열에 추가 → 응답
 * 2. 비동기: 스케줄러가 대기열에서 꺼내 DB에 반영
 *
 * 장점:
 * - 분산락 없이 Redis 원자 연산으로 동시성 제어
 * - DB 트랜잭션 부하 분산 (벌크 처리)
 * - 즉시 응답으로 사용자 경험 개선
 */
@Slf4j
@Service("asyncCouponIssueService")
@RequiredArgsConstructor
public class AsyncCouponIssueService implements CouponIssuer {

    private final CouponRedisRepository couponRedisRepository;
    private final CouponRepository couponRepository;

    @Override
    public CouponIssueResult issue(Long userId, Long couponId) {
        // 쿠폰 정보 조회 (최대 발급 수량 확인)
        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            return CouponIssueResult.INVALID_COUPON;
        }

        if (!coupon.canIssue()) {
            return CouponIssueResult.SOLD_OUT;
        }

        // Redis에서 발급 처리
        return couponRedisRepository.tryIssue(userId, couponId, coupon.getMaxIssueCount());
    }

    @Override
    public boolean isIssued(Long userId, Long couponId) {
        return couponRedisRepository.isIssued(userId, couponId);
    }

    /**
     * 현재 발급된 수량 조회
     */
    public long getIssuedCount(Long couponId) {
        return couponRedisRepository.getIssuedCount(couponId);
    }

    /**
     * 쿠폰 Redis 초기화
     */
    public void initializeCoupon(Long couponId) {
        couponRedisRepository.initializeCoupon(couponId);
    }
}
