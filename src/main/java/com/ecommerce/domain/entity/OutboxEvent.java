package com.ecommerce.domain.entity;

import com.ecommerce.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 아웃박스 이벤트 Entity
 * 외부 시스템으로 전송할 이벤트를 임시 저장합니다.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public OutboxEvent(String eventType, String payload) {
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("이벤트 타입은 필수입니다");
        }
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("페이로드는 필수입니다");
        }

        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
    }

    public boolean canRetry(int maxRetryCount) {
        return this.retryCount < maxRetryCount && this.status != OutboxStatus.PROCESSED;
    }
}
