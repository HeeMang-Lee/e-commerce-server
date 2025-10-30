package com.ecommerce.api;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.ProductDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Product", description = "상품 API")
@RequestMapping("/api/products")
public interface ProductApi {

    @Operation(summary = "상품 목록 조회", description = "전체 상품 목록을 조회합니다.")
    @GetMapping
    ApiResponse<ProductDto.ListResponse> getProducts();

    @Operation(summary = "상품 상세 조회", description = "특정 상품의 상세 정보를 조회합니다.")
    @GetMapping("/{productId}")
    ApiResponse<ProductDto.Response> getProduct(
            @Parameter(description = "상품 ID", required = true, example = "1")
            @PathVariable Long productId
    );

    @Operation(summary = "인기 상품 Top 5 조회", description = "최근 3일간 판매량 기준 인기 상품 상위 5개를 조회합니다.")
    @GetMapping("/top")
    ApiResponse<ProductDto.ListResponse> getTopProducts();
}
