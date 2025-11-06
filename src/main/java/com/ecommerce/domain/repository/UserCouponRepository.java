package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.UserCoupon;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 쿠폰 Repository 인터페이스
 */
public interface UserCouponRepository {

    /**
     * 사용자 쿠폰을 저장합니다.
     *
     * @param userCoupon 저장할 사용자 쿠폰
     * @return 저장된 사용자 쿠폰
     */
    UserCoupon save(UserCoupon userCoupon);

    /**
     * ID로 사용자 쿠폰을 조회합니다.
     *
     * @param id 사용자 쿠폰 ID
     * @return 사용자 쿠폰 Optional
     */
    Optional<UserCoupon> findById(Long id);

    /**
     * 사용자 ID로 쿠폰 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자 쿠폰 목록
     */
    List<UserCoupon> findByUserId(Long userId);
}
