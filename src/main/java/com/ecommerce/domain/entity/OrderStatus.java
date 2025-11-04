package com.ecommerce.domain.entity;

/**
 * 주문 상태
 */
public enum OrderStatus {
    PENDING,      // 결제 대기
    COMPLETED,    // 결제 완료
    CANCELLED     // 취소
}
