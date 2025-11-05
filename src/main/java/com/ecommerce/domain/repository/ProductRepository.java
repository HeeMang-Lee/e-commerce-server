package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.Product;

import java.util.List;
import java.util.Optional;

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
     * ID로 상품을 조회하고 락을 획득합니다. (동시성 제어용)
     *
     * @param id 상품 ID
     * @return 상품 Optional
     */
    Optional<Product> findByIdWithLock(Long id);
}
