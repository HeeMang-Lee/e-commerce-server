package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.repository.CouponRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * InMemory Coupon Repository 구현
 * 동시성 제어를 위한 Fair Lock + Timeout 패턴 적용
 */
@Slf4j
@Repository
public class InMemoryCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> store = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    private static final long LOCK_TIMEOUT_SECONDS = 5;

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            coupon.setId(idGenerator.getAndIncrement());
        }
        store.put(coupon.getId(), coupon);
        locks.putIfAbsent(coupon.getId(), new ReentrantLock(true)); // Fair lock
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Coupon> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public <R> R executeWithLock(Long couponId, Function<Coupon, R> operation) {
        ReentrantLock lock = locks.computeIfAbsent(couponId, k -> new ReentrantLock(true));

        boolean acquired = false;
        try {
            // Timeout을 두어 Deadlock 방지
            acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.error("Lock 획득 실패 (timeout): couponId={}", couponId);
                throw new IllegalStateException("쿠폰 락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.");
            }

            // 쿠폰 조회
            Coupon coupon = store.get(couponId);
            if (coupon == null) {
                throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다");
            }

            // 작업 실행 (Read -> Modify 포함)
            R result = operation.apply(coupon);

            // 변경사항 저장
            store.put(couponId, coupon);

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock 획득 중 인터럽트 발생: couponId={}", couponId, e);
            throw new IllegalStateException("쿠폰 처리 중 오류가 발생했습니다", e);
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }
}
