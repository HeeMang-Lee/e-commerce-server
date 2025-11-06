package com.ecommerce.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private Long userCouponId;  // optional - 사용할 쿠폰 ID
    private Integer usePoint;    // optional - 사용할 포인트
}
