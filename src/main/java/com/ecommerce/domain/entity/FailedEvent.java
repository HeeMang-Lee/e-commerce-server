package com.ecommerce.domain.entity;

import com.ecommerce.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DLT(Dead Letter Topic)에서 받은 실패 이벤트 저장용 Entity
 * 스케줄러가 주기적으로 재처리를 시도한다.
 */
@Entity
@Table(name = "failed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedEvent extends BaseEntity {

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "event_key", length = 100)
    private String eventKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FailedEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    private static final int BASE_DELAY_SECONDS = 30;

    public FailedEvent(String topic, String eventKey, String payload, String errorMessage) {
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.status = FailedEventStatus.PENDING;
        this.retryCount = 0;
        this.maxRetryCount = 3;
        this.createdAt = LocalDateTime.now();
        this.nextRetryAt = LocalDateTime.now().plusSeconds(BASE_DELAY_SECONDS);
    }

    public void retry() {
        this.status = FailedEventStatus.RETRYING;
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
    }

    /**
     * 지수 백오프로 다음 재시도 시간 예약
     * 30초 → 1분 → 2분 → 4분...
     */
    public void scheduleNextRetry() {
        long delaySeconds = (long) (BASE_DELAY_SECONDS * Math.pow(2, retryCount));
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
    }

    /**
     * 테스트용: 즉시 재시도 가능하도록 nextRetryAt을 과거로 설정
     */
    public void setNextRetryAtForTest(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public void markAsRecovered() {
        this.status = FailedEventStatus.RECOVERED;
        this.recoveredAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        if (this.retryCount >= this.maxRetryCount) {
            this.status = FailedEventStatus.ABANDONED;
            this.nextRetryAt = null;
        } else {
            this.status = FailedEventStatus.PENDING;
            scheduleNextRetry();
        }
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetryCount
                && this.status != FailedEventStatus.RECOVERED
                && this.status != FailedEventStatus.ABANDONED;
    }

    /**
     * 지금 재시도해야 하는지 확인
     * nextRetryAt이 현재 시간보다 이전이면 재시도
     */
    public boolean shouldRetryNow() {
        if (!canRetry()) return false;
        if (this.nextRetryAt == null) return true;
        return this.nextRetryAt.isBefore(LocalDateTime.now()) || this.nextRetryAt.isEqual(LocalDateTime.now());
    }
}
