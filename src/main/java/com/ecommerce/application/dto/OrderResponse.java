package com.ecommerce.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private String orderNumber;
    private Integer totalAmount;
    private Integer discountAmount;
    private Integer usedPoint;
    private Integer finalAmount;
}
