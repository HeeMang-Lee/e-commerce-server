package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class ProductDto {

    @Schema(description = "상품 응답")
    public record Response(
        @Schema(description = "상품 ID", example = "1")
        Long productId,

        @Schema(description = "상품명", example = "무선 키보드")
        String name,

        @Schema(description = "가격", example = "89000")
        Integer price,

        @Schema(description = "재고 수량", example = "100")
        Integer stock
    ) {}

    @Schema(description = "상품 목록 응답")
    public record ListResponse(
        @Schema(description = "상품 목록")
        List<Response> products,

        @Schema(description = "전체 상품 수", example = "5")
        int totalCount
    ) {}

    @Schema(description = "인기 상품 응답")
    public record TopProductResponse(
        @Schema(description = "상품 ID", example = "1")
        Long productId,

        @Schema(description = "상품명", example = "무선 키보드")
        String name,

        @Schema(description = "가격", example = "89000")
        Integer price,

        @Schema(description = "판매 수량", example = "150")
        Integer salesCount
    ) {}
}
