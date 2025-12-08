package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.UserCoupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    List<UserCoupon> saveAll(List<UserCoupon> userCoupons);

    Optional<UserCoupon> findById(Long id);

    default UserCoupon getByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + id));
    }

    List<UserCoupon> findByUserId(Long userId);

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCoupon> findByCouponIdAndUserIdIn(Long couponId, List<Long> userIds);

    List<UserCoupon> findAll();

    void deleteAll();
}
