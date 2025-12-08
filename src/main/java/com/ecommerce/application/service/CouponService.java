package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.ecommerce.domain.service.CouponDomainService;
import com.ecommerce.domain.service.CouponIssueResult;
import com.ecommerce.domain.service.CouponIssuer;
import com.ecommerce.infrastructure.lock.DistributedLockExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 쿠폰 Application Facade 서비스
 *
 * 책임:
 * - 분산 락 관리 (동시성 제어)
 * - 도메인 서비스 호출 조율
 * - DTO 변환
 *
 * 주의:
 * - 비즈니스 로직은 CouponDomainService에 위임
 * - Self-Invocation 없음
 */
@Service
public class CouponService {

    private static final String LOCK_KEY_PREFIX_COUPON = "lock:coupon:";
    private static final String LOCK_KEY_PREFIX_USERCOUPON = "lock:usercoupon:";

    private final UserCouponRepository userCouponRepository;
    private final DistributedLockExecutor lockExecutor;
    private final CouponDomainService couponDomainService;
    private final CouponIssuer asyncCouponIssuer;

    public CouponService(
            UserCouponRepository userCouponRepository,
            DistributedLockExecutor lockExecutor,
            CouponDomainService couponDomainService,
            @Qualifier("asyncCouponIssueService") CouponIssuer asyncCouponIssuer) {
        this.userCouponRepository = userCouponRepository;
        this.lockExecutor = lockExecutor;
        this.couponDomainService = couponDomainService;
        this.asyncCouponIssuer = asyncCouponIssuer;
    }

    public UserCouponResponse issueCoupon(CouponIssueRequest request) {
        String lockKey = LOCK_KEY_PREFIX_COUPON + request.couponId();

        return lockExecutor.executeWithLock(lockKey, () -> {
            UserCoupon userCoupon = couponDomainService.issueCoupon(request.userId(), request.couponId());
            return UserCouponResponse.from(userCoupon);
        });
    }

    public UserCouponResponse useCoupon(Long userCouponId) {
        String lockKey = LOCK_KEY_PREFIX_USERCOUPON + userCouponId;

        return lockExecutor.executeWithLock(lockKey, () -> {
            UserCoupon userCoupon = couponDomainService.useCoupon(userCouponId);
            return UserCouponResponse.from(userCoupon);
        });
    }

    public List<UserCouponResponse> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId).stream()
                .map(UserCouponResponse::from)
                .toList();
    }

    public CouponIssueResult issueCouponAsync(Long userId, Long couponId) {
        return asyncCouponIssuer.issue(userId, couponId);
    }
}
