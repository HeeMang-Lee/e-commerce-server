package com.ecommerce.domain.entity;

/**
 * 아웃박스 이벤트 상태
 */
public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}
