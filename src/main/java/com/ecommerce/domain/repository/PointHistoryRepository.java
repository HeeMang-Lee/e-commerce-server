package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.PointHistory;

import java.util.List;

/**
 * 포인트 이력 Repository 인터페이스
 */
public interface PointHistoryRepository {

    PointHistory save(PointHistory history);

    List<PointHistory> findByUserId(Long userId);

    void deleteAll();
}
