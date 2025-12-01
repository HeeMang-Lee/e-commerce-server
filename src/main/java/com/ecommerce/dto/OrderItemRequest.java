package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주문 상품 요청")
public record OrderItemRequest(
    @Schema(description = "상품 ID", example = "1", required = true)
    Long productId,

    @Schema(description = "수량", example = "2", required = true)
    Integer quantity
) {}
