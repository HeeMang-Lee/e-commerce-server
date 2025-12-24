package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.FailedEvent;
import com.ecommerce.domain.entity.FailedEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JpaFailedEventRepository extends JpaRepository<FailedEvent, Long> {

    List<FailedEvent> findByStatus(FailedEventStatus status);

    @Query("SELECT e FROM FailedEvent e WHERE e.status = 'PENDING' AND e.retryCount < e.maxRetryCount")
    List<FailedEvent> findRetryableEvents();

    /**
     * 재시도 시간이 된 이벤트 조회 (지수 백오프 기반)
     * status = PENDING AND retryCount < maxRetryCount AND nextRetryAt <= now
     */
    @Query("SELECT e FROM FailedEvent e WHERE e.status = 'PENDING' AND e.retryCount < e.maxRetryCount AND e.nextRetryAt <= :now")
    List<FailedEvent> findRetryableEventsNow(@Param("now") LocalDateTime now);
}
