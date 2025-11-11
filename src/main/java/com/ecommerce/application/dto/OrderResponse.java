package com.ecommerce.application.dto;

public record OrderResponse(
    Long orderId,
    String orderNumber,
    Integer totalAmount,
    Integer discountAmount,
    Integer usedPoint,
    Integer finalAmount
) {}
