package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ProductDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품 응답")
    public static class Response {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품명", example = "무선 키보드")
        private String name;

        @Schema(description = "가격", example = "89000")
        private Integer price;

        @Schema(description = "재고 수량", example = "100")
        private Integer stock;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품 목록 응답")
    public static class ListResponse {
        @Schema(description = "상품 목록")
        private List<Response> products;

        @Schema(description = "전체 상품 수", example = "5")
        private int totalCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "인기 상품 응답")
    public static class TopProductResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품명", example = "무선 키보드")
        private String name;

        @Schema(description = "가격", example = "89000")
        private Integer price;

        @Schema(description = "판매 수량", example = "150")
        private Integer salesCount;
    }
}
