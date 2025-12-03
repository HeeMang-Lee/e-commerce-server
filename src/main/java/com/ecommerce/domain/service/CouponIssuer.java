package com.ecommerce.domain.service;

/**
 * 쿠폰 발급 인터페이스
 *
 * 쿠폰 발급 로직을 추상화하여 다양한 구현체를 지원합니다:
 * - 기존 동기 방식 (분산락 + DB)
 * - Redis 기반 비동기 방식
 *
 * 비동기 방식에서는 발급 요청 즉시 응답하고,
 * 실제 DB 반영은 스케줄러가 처리합니다.
 */
public interface CouponIssuer {

    /**
     * 쿠폰 발급 요청
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급 결과
     */
    CouponIssueResult issue(Long userId, Long couponId);

    /**
     * 쿠폰 발급 여부 확인
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급되었으면 true
     */
    boolean isIssued(Long userId, Long couponId);
}
