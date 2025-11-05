package com.ecommerce.domain.entity;

/**
 * 주문 항목 상태
 */
public enum OrderItemStatus {
    PENDING,      // 대기
    CONFIRMED,    // 확정
    CANCELLED,    // 취소
    REFUNDED      // 환불
}
