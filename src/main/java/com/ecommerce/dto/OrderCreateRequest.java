package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "주문 생성 요청")
public record OrderCreateRequest(
    @Schema(description = "사용자 ID", example = "1", required = true)
    Long userId,

    @Schema(description = "주문 상품 목록", required = true)
    List<OrderItemRequest> items,

    @Schema(description = "사용자 쿠폰 ID", example = "1")
    Long userCouponId
) {}
