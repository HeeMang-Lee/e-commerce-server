package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "상품 목록 응답")
public record ProductListResponse(
    @Schema(description = "상품 목록")
    List<ProductResponse> products,

    @Schema(description = "전체 상품 수", example = "5")
    int totalCount
) {}
