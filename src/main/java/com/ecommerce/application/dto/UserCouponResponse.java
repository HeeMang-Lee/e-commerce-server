package com.ecommerce.application.dto;

import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.entity.UserCouponStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserCouponResponse {
    private Long id;
    private Long userId;
    private Long couponId;
    private UserCouponStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

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
