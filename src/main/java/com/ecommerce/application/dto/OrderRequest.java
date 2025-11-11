package com.ecommerce.application.dto;

import java.util.List;

public record OrderRequest(
    Long userId,
    List<OrderItemRequest> items,
    Long userCouponId,  // optional
    Integer usePoint     // optional
) {

    public record OrderItemRequest(
        Long productId,
        Integer quantity
    ) {}
}
