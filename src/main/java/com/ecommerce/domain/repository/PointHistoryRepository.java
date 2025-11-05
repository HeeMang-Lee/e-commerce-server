package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.PointHistory;

import java.util.List;

/**
 * 포인트 이력 Repository 인터페이스
 */
public interface PointHistoryRepository {

    /**
     * 포인트 이력을 저장합니다.
     *
     * @param history 저장할 포인트 이력
     * @return 저장된 포인트 이력
     */
    PointHistory save(PointHistory history);

    /**
     * 사용자 ID로 포인트 이력 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 포인트 이력 목록
     */
    List<PointHistory> findByUserId(Long userId);
}
