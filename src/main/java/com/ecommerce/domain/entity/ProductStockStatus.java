package com.ecommerce.domain.entity;

/**
 * 상품 재고 상태
 */
public enum ProductStockStatus {
    /**
     * 판매 중 (재고 충분)
     */
    AVAILABLE,

    /**
     * 재고 부족 (10개 미만)
     */
    LOW_STOCK,

    /**
     * 품절
     */
    SOLD_OUT
}
