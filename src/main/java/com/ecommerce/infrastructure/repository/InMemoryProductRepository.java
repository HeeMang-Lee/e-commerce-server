package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.ProductRepository;
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
 * InMemory Product Repository 구현
 * 동시성 제어를 위한 Fair Lock + Timeout 패턴 적용
 */
@Slf4j
@Repository
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    private static final long LOCK_TIMEOUT_SECONDS = 5;

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(idGenerator.getAndIncrement());
        }
        store.put(product.getId(), product);
        locks.putIfAbsent(product.getId(), new ReentrantLock(true)); // Fair lock
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public <R> R executeWithLock(Long productId, Function<Product, R> operation) {
        ReentrantLock lock = locks.computeIfAbsent(productId, k -> new ReentrantLock(true));

        boolean acquired = false;
        try {
            // Timeout을 두어 Deadlock 방지
            acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.error("Lock 획득 실패 (timeout): productId={}", productId);
                throw new IllegalStateException("상품 락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.");
            }

            // 상품 조회
            Product product = store.get(productId);
            if (product == null) {
                throw new IllegalArgumentException("상품을 찾을 수 없습니다");
            }

            // 작업 실행 (Read -> Modify 포함)
            R result = operation.apply(product);

            // 변경사항 저장
            store.put(productId, product);

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock 획득 중 인터럽트 발생: productId={}", productId, e);
            throw new IllegalStateException("상품 처리 중 오류가 발생했습니다", e);
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }
}
