package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.repository.PopularProductRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PopularProductRepositoryImpl implements PopularProductRepository {

    private final EntityManager entityManager;

    @Override
    public List<Long> getTopProductIds(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        String sql = """
            SELECT product_id
            FROM popular_products_view
            WHERE last_updated >= :startTime
            ORDER BY sales_count DESC
            LIMIT :limit
            """;

        return entityManager.createNativeQuery(sql, Long.class)
                .setParameter("startTime", startTime)
                .setParameter("limit", limit)
                .getResultList();
    }
}
