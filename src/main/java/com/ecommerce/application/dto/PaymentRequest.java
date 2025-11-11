package com.ecommerce.application.dto;

public record PaymentRequest(
    Long userCouponId,  // optional - 사용할 쿠폰 ID
    Integer usePoint     // optional - 사용할 포인트
) {}
