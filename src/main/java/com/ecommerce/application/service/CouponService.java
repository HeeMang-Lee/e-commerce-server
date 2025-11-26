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

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final RedissonClient redissonClient;

    /**
     * 쿠폰을 발급합니다.
     * Redis 분산 락을 사용하여 재고 차감과 사용자 쿠폰 생성의 원자성을 보장합니다.
     */
    public UserCouponResponse issueCoupon(CouponIssueRequest request) {
        // 중복 발급 체크
        userCouponRepository.findByUserIdAndCouponId(request.userId(), request.couponId())
                .ifPresent(existingCoupon -> {
                    throw new IllegalStateException("이미 발급받은 쿠폰입니다");
                });

        String lockKey = LOCK_KEY_PREFIX_COUPON + request.couponId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
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

    /**
     * 쿠폰 발급 트랜잭션 처리 (락 획득 후 실행)
     */
    @Transactional
    private UserCoupon executeIssueCoupon(CouponIssueRequest request) {
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

    /**
     * 쿠폰을 사용합니다.
     * Redis 분산 락을 사용하여 동시성 제어를 수행합니다.
     */
    public UserCouponResponse useCoupon(Long userCouponId) {
        String lockKey = LOCK_KEY_PREFIX_USERCOUPON + userCouponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
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

    /**
     * 쿠폰 사용 트랜잭션 처리 (락 획득 후 실행)
     */
    @Transactional
    private UserCoupon executeUseCoupon(Long userCouponId) {
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
