package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰을 발급합니다.
     * Lock을 사용하여 재고 차감과 사용자 쿠폰 생성의 원자성을 보장합니다.
     */
    public UserCouponResponse issueCoupon(CouponIssueRequest request) {
        UserCoupon userCoupon = couponRepository.executeWithLock(
                request.couponId(),
                coupon -> {
                    coupon.issue();

                    LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
                    UserCoupon newUserCoupon = new UserCoupon(
                            request.userId(),
                            request.couponId(),
                            expiresAt
                    );
                    userCouponRepository.save(newUserCoupon);

                    return newUserCoupon;
                }
        );

        return UserCouponResponse.from(userCoupon);
    }

    public List<UserCouponResponse> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId).stream()
                .map(UserCouponResponse::from)
                .toList();
    }
}
