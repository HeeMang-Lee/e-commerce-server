package com.ecommerce.domain.service;

import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 쿠폰 도메인 서비스
 *
 * 책임:
 * - 쿠폰 발급의 핵심 비즈니스 로직
 * - 쿠폰 사용 처리
 * - 트랜잭션 경계 관리
 *
 * 주의:
 * - 다른 도메인 서비스에 의존하지 않음
 * - 분산 락은 상위 Application Service에서 관리
 */
@Service
@RequiredArgsConstructor
public class CouponDomainService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 발급
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰
     */
    @Transactional
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        // 중복 발급 체크
        userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .ifPresent(existingCoupon -> {
                    throw new IllegalStateException("이미 발급받은 쿠폰입니다");
                });

        // 쿠폰 발급 수량 증가
        Coupon coupon = couponRepository.getByIdOrThrow(couponId);
        coupon.issue();
        couponRepository.save(coupon);

        // 사용자 쿠폰 생성
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
        UserCoupon newUserCoupon = new UserCoupon(userId, couponId, expiresAt);
        userCouponRepository.save(newUserCoupon);

        return newUserCoupon;
    }

    /**
     * 쿠폰 사용
     *
     * @param userCouponId 사용자 쿠폰 ID
     * @return 사용된 쿠폰
     */
    @Transactional
    public UserCoupon useCoupon(Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(userCouponId);
        userCoupon.use();
        userCouponRepository.save(userCoupon);
        return userCoupon;
    }

    /**
     * 쿠폰 사용 취소 (보상 트랜잭션)
     *
     * @param userCouponId 사용자 쿠폰 ID
     * @return 복구된 쿠폰
     */
    @Transactional
    public UserCoupon cancelCouponUsage(Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(userCouponId);
        userCoupon.restore();
        userCouponRepository.save(userCoupon);
        return userCoupon;
    }
}
