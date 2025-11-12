package com.ecommerce.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface PopularProductRepository {
    /**
     * 특정 기간 동안 판매량 기준 인기 상품 조회
     * View에서 집계된 데이터를 조회합니다.
     */
    List<Long> getTopProductIds(LocalDateTime startTime, LocalDateTime endTime, int limit);
}
