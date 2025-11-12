package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final JpaCouponRepository jpaCouponRepository;

    @Override
    public Coupon save(Coupon coupon) {
        return jpaCouponRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return jpaCouponRepository.findById(id);
    }

    @Override
    public List<Coupon> findAll() {
        return jpaCouponRepository.findAll();
    }

    @Override
    @Transactional
    public <R> R executeWithLock(Long couponId, Function<Coupon, R> operation) {
        Coupon coupon = jpaCouponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));

        R result = operation.apply(coupon);
        jpaCouponRepository.save(coupon);

        return result;
    }

    @Override
    public void deleteAll() {
        jpaCouponRepository.deleteAll();
    }
}
