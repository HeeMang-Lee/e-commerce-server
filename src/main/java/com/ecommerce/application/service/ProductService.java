package com.ecommerce.application.service;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.PopularProductRepository;
import com.ecommerce.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 서비스
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final PopularProductRepository popularProductRepository;

    /**
     * 모든 상품 목록을 조회합니다.
     */
    public List<ProductResponse> getProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * 상품을 단건 조회합니다.
     */
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.getByIdOrThrow(productId);
        return ProductResponse.from(product);
    }

    /**
     * 최근 3일간 판매량 기준 인기 상품 Top 5를 조회합니다.
     */
    public List<ProductResponse> getTopProductsLast3Days() {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(3);

        List<Long> topProductIds = popularProductRepository.getTopProductIds(startTime, endTime, 5);

        return topProductIds.stream()
                .map(productRepository::getByIdOrThrow)
                .map(ProductResponse::from)
                .toList();
    }
}
