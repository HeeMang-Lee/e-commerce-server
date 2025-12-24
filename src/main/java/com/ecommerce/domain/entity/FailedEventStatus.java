package com.ecommerce.domain.entity;

public enum FailedEventStatus {
    PENDING,      // 재처리 대기
    RETRYING,     // 재처리 중
    RECOVERED,    // 재처리 성공
    ABANDONED     // 재처리 포기 (최대 횟수 초과)
}
