package com.ecommerce.domain.entity;

/**
 * 아웃박스 이벤트 상태
 */
public enum OutboxStatus {
    PENDING,    // 전송 대기 중
    PROCESSED,  // 전송 완료
    FAILED      // 전송 실패
}
