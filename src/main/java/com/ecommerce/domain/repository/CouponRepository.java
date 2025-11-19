package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.Coupon;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 쿠폰 Repository 인터페이스
 */
public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long id);

    List<Coupon> findAll();

    /**
     * 동시성 제어를 위한 락 기반 트랜잭션 실행
     * Read -> Modify -> Save 전체 구간을 락으로 보호합니다.
     */
    <R> R executeWithLock(Long couponId, Function<Coupon, R> operation);

    void deleteAll();
}
