package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.PopularProduct;
import com.ecommerce.domain.repository.PopularProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PopularProductRepositoryImpl implements PopularProductRepository {

    private final JpaPopularProductRepository jpaPopularProductRepository;

    @Override
    @Transactional
    public void recordSale(Long productId, Integer quantity, LocalDateTime orderTime) {
        PopularProduct popularProduct = jpaPopularProductRepository.findByProductId(productId)
                .orElseGet(() -> new PopularProduct(productId));

        popularProduct.incrementSales(quantity);
        jpaPopularProductRepository.save(popularProduct);
    }

    @Override
    public List<Long> getTopProductIds(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        List<Long> productIds = jpaPopularProductRepository
                .findTopProductIdsByTimeRange(startTime, endTime);

        return productIds.stream()
                .limit(limit)
                .toList();
    }
}
