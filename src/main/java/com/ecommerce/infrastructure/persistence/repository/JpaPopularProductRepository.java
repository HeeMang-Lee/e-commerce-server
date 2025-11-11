package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.PopularProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaPopularProductRepository extends JpaRepository<PopularProduct, Long> {

    Optional<PopularProduct> findByProductId(Long productId);

    @Query("SELECT pp.productId FROM PopularProduct pp " +
           "WHERE pp.lastUpdated BETWEEN :startTime AND :endTime " +
           "ORDER BY pp.salesCount DESC")
    List<Long> findTopProductIdsByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
