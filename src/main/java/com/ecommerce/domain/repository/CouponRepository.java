package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.Coupon;

import java.util.List;
import java.util.Optional;

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
     * ID로 쿠폰을 조회하고 락을 획득합니다. (동시성 제어용)
     *
     * @param id 쿠폰 ID
     * @return 쿠폰 Optional
     */
    Optional<Coupon> findByIdWithLock(Long id);

    /**
     * 모든 쿠폰을 조회합니다.
     *
     * @return 쿠폰 목록
     */
    List<Coupon> findAll();
}
