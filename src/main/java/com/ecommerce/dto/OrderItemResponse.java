package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주문 상품 응답")
public record OrderItemResponse(
    @Schema(description = "상품 ID", example = "1")
    Long productId,

    @Schema(description = "상품명", example = "무선 키보드")
    String productName,

    @Schema(description = "수량", example = "2")
    Integer quantity,

    @Schema(description = "단가", example = "89000")
    Integer price,

    @Schema(description = "소계", example = "178000")
    Integer subtotal
) {}
