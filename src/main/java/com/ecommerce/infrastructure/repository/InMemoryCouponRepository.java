package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.repository.CouponRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * InMemory Coupon Repository 구현
 * 동시성 제어를 위한 Lock 포함
 */
@Repository
public class InMemoryCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> store = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            coupon.setId(idGenerator.getAndIncrement());
        }
        store.put(coupon.getId(), coupon);
        locks.putIfAbsent(coupon.getId(), new ReentrantLock());
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Coupon> findByIdWithLock(Long id) {
        ReentrantLock lock = locks.computeIfAbsent(id, k -> new ReentrantLock());
        lock.lock();
        try {
            return Optional.ofNullable(store.get(id));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Coupon> findAll() {
        return new ArrayList<>(store.values());
    }
}
