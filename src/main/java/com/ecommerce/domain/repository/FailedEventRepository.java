package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.FailedEvent;
import com.ecommerce.domain.entity.FailedEventStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DLT 실패 이벤트 Repository 인터페이스
 */
public interface FailedEventRepository {

    FailedEvent save(FailedEvent event);

    List<FailedEvent> findByStatus(FailedEventStatus status);

    List<FailedEvent> findRetryableEvents();

    /**
     * 재시도 시간이 된 이벤트 조회 (지수 백오프 기반)
     */
    List<FailedEvent> findRetryableEventsNow(LocalDateTime now);

    void deleteAll();
}
