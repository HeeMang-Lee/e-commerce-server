package com.ecommerce.application.dto;

import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.entity.UserCouponStatus;

import java.time.LocalDateTime;

public record UserCouponResponse(
    Long id,
    Long userId,
    Long couponId,
    UserCouponStatus status,
    LocalDateTime issuedAt,
    LocalDateTime expiresAt
) {

    public static UserCouponResponse from(UserCoupon userCoupon) {
        return new UserCouponResponse(
                userCoupon.getId(),
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                userCoupon.getStatus(),
                userCoupon.getIssuedAt(),
                userCoupon.getExpiresAt()
        );
    }
}
