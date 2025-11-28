package com.ecommerce.domain.service;

import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 도메인 서비스
 *
 * 책임:
 * - 상품 재고 관리의 핵심 비즈니스 로직
 * - 재고 차감/복구
 * - 트랜잭션 경계 관리
 *
 * 주의:
 * - 다른 도메인 서비스에 의존하지 않음
 * - 분산 락은 상위 Application Service에서 관리
 */
@Service
@RequiredArgsConstructor
public class ProductDomainService {

    private final ProductRepository productRepository;

    /**
     * 재고 차감
     *
     * @param productId 상품 ID
     * @param quantity 차감 수량
     * @return 차감된 상품
     */
    @Transactional
    public Product reduceStock(Long productId, int quantity) {
        Product product = productRepository.getByIdOrThrow(productId);
        product.reduceStock(quantity);
        productRepository.save(product);
        return product;
    }

    /**
     * 재고 복구
     *
     * @param productId 상품 ID
     * @param quantity 복구 수량
     */
    @Transactional
    public void restoreStock(Long productId, int quantity) {
        Product product = productRepository.getByIdOrThrow(productId);
        product.restoreStock(quantity);
        productRepository.save(product);
    }
}
