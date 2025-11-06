package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.Coupon;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 쿠폰 Repository 인터페이스
 */
public interface CouponRepository {

    /**
     * 쿠폰을 저장합니다.
     *
     * @param coupon 저장할 쿠폰
     * @return 저장된 쿠폰
     */
    Coupon save(Coupon coupon);

    /**
     * ID로 쿠폰을 조회합니다.
     *
     * @param id 쿠폰 ID
     * @return 쿠폰 Optional
     */
    Optional<Coupon> findById(Long id);

    /**
     * 모든 쿠폰을 조회합니다.
     *
     * @return 쿠폰 목록
     */
    List<Coupon> findAll();

    /**
     * 동시성 제어를 위한 락 기반 트랜잭션 실행
     * Read -> Modify -> Save 전체 구간을 락으로 보호합니다.
     *
     * @param couponId 쿠폰 ID
     * @param operation 락 보호 하에 실행할 작업
     * @param <R> 작업 결과 타입
     * @return 작업 결과
     */
    <R> R executeWithLock(Long couponId, Function<Coupon, R> operation);
}
