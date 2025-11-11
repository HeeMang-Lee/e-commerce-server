package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InMemory UserCoupon Repository 구현
 */
@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getId() == null) {
            userCoupon.setId(idGenerator.getAndIncrement());
        }
        store.put(userCoupon.getId(), userCoupon);
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return store.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .toList();
    }
}
