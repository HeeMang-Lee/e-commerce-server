package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.ecommerce.domain.service.CouponDomainService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
@RequiredArgsConstructor
public class CouponService {

    private static final String LOCK_KEY_PREFIX_COUPON = "lock:coupon:";
    private static final String LOCK_KEY_PREFIX_USERCOUPON = "lock:usercoupon:";
    private static final long LOCK_WAIT_TIME_SECONDS = 30;
    private static final long LOCK_LEASE_TIME_SECONDS = 10;

    private final UserCouponRepository userCouponRepository;
    private final RedissonClient redissonClient;
    private final CouponDomainService couponDomainService;

    public UserCouponResponse issueCoupon(CouponIssueRequest request) {
        String lockKey = LOCK_KEY_PREFIX_COUPON + request.couponId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("쿠폰 발급 락 획득 실패: couponId=" + request.couponId());
            }

            UserCoupon userCoupon = couponDomainService.issueCoupon(request.userId(), request.couponId());
            return UserCouponResponse.from(userCoupon);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public UserCouponResponse useCoupon(Long userCouponId) {
        String lockKey = LOCK_KEY_PREFIX_USERCOUPON + userCouponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("쿠폰 사용 락 획득 실패: userCouponId=" + userCouponId);
            }

            UserCoupon userCoupon = couponDomainService.useCoupon(userCouponId);
            return UserCouponResponse.from(userCoupon);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public List<UserCouponResponse> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId).stream()
                .map(UserCouponResponse::from)
                .toList();
    }
}
