package com.ecommerce.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface PopularProductRepository {
    /**
     * 상품 판매 이력 기록
     */
    void recordSale(Long productId, Integer quantity, LocalDateTime orderTime);

    /**
     * 특정 기간 동안 판매량 기준 인기 상품 조회
     */
    List<Long> getTopProductIds(LocalDateTime startTime, LocalDateTime endTime, int limit);
}
