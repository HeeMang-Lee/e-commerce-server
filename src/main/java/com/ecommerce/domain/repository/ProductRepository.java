package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.Product;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    default Product getByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
    }

    List<Product> findAll();

    List<Product> findAllById(List<Long> ids);

    void deleteAll();

    /**
     * 동시성 제어를 위한 락 기반 트랜잭션 실행
     * Read -> Modify -> Save 전체 구간을 락으로 보호합니다.
     */
    <R> R executeWithLock(Long productId, Function<Product, R> operation);
}
