package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.UserCoupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(Long id);

    default UserCoupon getByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + id));
    }

    List<UserCoupon> findByUserId(Long userId);

    void deleteAll();
}
