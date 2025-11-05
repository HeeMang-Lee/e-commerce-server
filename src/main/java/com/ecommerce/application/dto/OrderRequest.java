package com.ecommerce.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private Long userId;
    private List<OrderItemRequest> items;
    private Long userCouponId;  // optional
    private Integer usePoint;    // optional

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        private Long productId;
        private Integer quantity;
    }
}
