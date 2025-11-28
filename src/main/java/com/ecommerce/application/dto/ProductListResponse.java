package com.ecommerce.application.dto;

import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.entity.ProductStockStatus;

/**
 * 상품 목록 조회 응답
 *
 * 재고 숫자 대신 상태만 표시하여 캐시 효율성 향상
 * - 재고 10개 미만: LOW_STOCK
 * - 재고 0개: SOLD_OUT
 * - 그 외: AVAILABLE
 */
public record ProductListResponse(
    Long id,
    String name,
    String description,
    Integer price,
    ProductStockStatus stockStatus
) {

    public static ProductListResponse from(Product product) {
        ProductStockStatus status = determineStockStatus(product.getStockQuantity());

        return new ProductListResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                status
        );
    }

    private static ProductStockStatus determineStockStatus(int stockQuantity) {
        if (stockQuantity == 0) {
            return ProductStockStatus.SOLD_OUT;
        } else if (stockQuantity < 10) {
            return ProductStockStatus.LOW_STOCK;
        } else {
            return ProductStockStatus.AVAILABLE;
        }
    }
}
