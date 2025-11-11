package com.ecommerce.application.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record PaymentRequest(
    Long userCouponId,  // optional - 사용할 쿠폰 ID

    @PositiveOrZero(message = "사용 포인트는 0 이상이어야 합니다")
    Integer usePoint     // optional - 사용할 포인트
) {}
