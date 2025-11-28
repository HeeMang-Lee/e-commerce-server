package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final String LOCK_KEY_PREFIX_COUPON = "lock:coupon:";
    private static final String LOCK_KEY_PREFIX_USERCOUPON = "lock:usercoupon:";
    private static final long LOCK_WAIT_TIME_SECONDS = 30;
    private static final long LOCK_LEASE_TIME_SECONDS = 10;

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final RedissonClient redissonClient;

    public UserCouponResponse issueCoupon(CouponIssueRequest request) {
        String lockKey = LOCK_KEY_PREFIX_COUPON + request.couponId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("쿠폰 발급 락 획득 실패: couponId=" + request.couponId());
            }

            UserCoupon userCoupon = executeIssueCoupon(request);
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

    @Transactional
    public UserCoupon executeIssueCoupon(CouponIssueRequest request) {
        userCouponRepository.findByUserIdAndCouponId(request.userId(), request.couponId())
                .ifPresent(existingCoupon -> {
                    throw new IllegalStateException("이미 발급받은 쿠폰입니다");
                });

        Coupon coupon = couponRepository.getByIdOrThrow(request.couponId());
        coupon.issue();
        couponRepository.save(coupon);

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
        UserCoupon newUserCoupon = new UserCoupon(
                request.userId(),
                request.couponId(),
                expiresAt
        );
        userCouponRepository.save(newUserCoupon);

        return newUserCoupon;
    }

    public UserCouponResponse useCoupon(Long userCouponId) {
        String lockKey = LOCK_KEY_PREFIX_USERCOUPON + userCouponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("쿠폰 사용 락 획득 실패: userCouponId=" + userCouponId);
            }

            UserCoupon userCoupon = executeUseCoupon(userCouponId);
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

    @Transactional
    public UserCoupon executeUseCoupon(Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(userCouponId);
        userCoupon.use();
        userCouponRepository.save(userCoupon);
        return userCoupon;
    }

    public List<UserCouponResponse> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId).stream()
                .map(UserCouponResponse::from)
                .toList();
    }
}
