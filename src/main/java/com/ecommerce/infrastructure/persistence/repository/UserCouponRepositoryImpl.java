package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final JpaUserCouponRepository jpaUserCouponRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return jpaUserCouponRepository.save(userCoupon);
    }

    @Override
    public List<UserCoupon> saveAll(List<UserCoupon> userCoupons) {
        return jpaUserCouponRepository.saveAll(userCoupons);
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return jpaUserCouponRepository.findById(id);
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return jpaUserCouponRepository.findByUserId(userId);
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return jpaUserCouponRepository.findByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public List<UserCoupon> findAll() {
        return jpaUserCouponRepository.findAll();
    }

    @Override
    public void deleteAll() {
        jpaUserCouponRepository.deleteAll();
    }
}
