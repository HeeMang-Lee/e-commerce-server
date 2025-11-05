package com.ecommerce.domain.entity;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 아웃박스 이벤트 Entity
 * 외부 시스템으로 전송할 이벤트를 임시 저장합니다.
 */
@Getter
public class OutboxEvent {

    private Long id;
    private final String eventType;
    private final String payload;
    private OutboxStatus status;
    private Integer retryCount;
    private final LocalDateTime createdAt;
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

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 이벤트 처리 완료 상태로 변경
     */
    public void markAsProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 이벤트 처리 실패 상태로 변경
     */
    public void markAsFailed() {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
    }

    /**
     * 재시도 가능 여부 확인
     */
    public boolean canRetry(int maxRetryCount) {
        return this.retryCount < maxRetryCount && this.status != OutboxStatus.PROCESSED;
    }
}
