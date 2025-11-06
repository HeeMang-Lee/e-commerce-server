package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.Product;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 상품 Repository 인터페이스
 */
public interface ProductRepository {

    /**
     * 상품을 저장합니다.
     *
     * @param product 저장할 상품
     * @return 저장된 상품
     */
    Product save(Product product);

    /**
     * ID로 상품을 조회합니다.
     *
     * @param id 상품 ID
     * @return 상품 Optional
     */
    Optional<Product> findById(Long id);

    /**
     * 모든 상품을 조회합니다.
     *
     * @return 상품 목록
     */
    List<Product> findAll();

    /**
     * 동시성 제어를 위한 락 기반 트랜잭션 실행
     * Read -> Modify -> Save 전체 구간을 락으로 보호합니다.
     *
     * @param productId 상품 ID
     * @param operation 락 보호 하에 실행할 작업
     * @param <R> 작업 결과 타입
     * @return 작업 결과
     */
    <R> R executeWithLock(Long productId, Function<Product, R> operation);
}
