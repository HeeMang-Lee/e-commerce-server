package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.entity.OutboxStatus;

import java.util.List;

/**
 * 아웃박스 이벤트 Repository 인터페이스
 */
public interface OutboxEventRepository {

    /**
     * 아웃박스 이벤트를 저장합니다.
     */
    OutboxEvent save(OutboxEvent event);

    /**
     * 특정 상태의 이벤트 목록을 조회합니다.
     */
    List<OutboxEvent> findByStatus(OutboxStatus status);

    void deleteAll();
}
