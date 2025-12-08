package com.ecommerce.infrastructure.redis;

import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.service.CouponIssuer;
import com.ecommerce.domain.service.CouponIssueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("asyncCouponIssueService")
@RequiredArgsConstructor
public class AsyncCouponIssueService implements CouponIssuer {

    private final CouponRedisRepository couponRedisRepository;
    private final CouponRepository couponRepository;

    @Override
    public CouponIssueResult issue(Long userId, Long couponId) {
        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            return CouponIssueResult.INVALID_COUPON;
        }

        if (!coupon.canIssue()) {
            return CouponIssueResult.SOLD_OUT;
        }

        return couponRedisRepository.tryIssue(userId, couponId, coupon.getMaxIssueCount());
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
