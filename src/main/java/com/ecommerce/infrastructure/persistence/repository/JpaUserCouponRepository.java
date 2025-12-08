package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaUserCouponRepository extends JpaRepository<UserCoupon, Long> {
    List<UserCoupon> findByUserId(Long userId);

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCoupon> findByCouponIdAndUserIdIn(Long couponId, List<Long> userIds);
}
