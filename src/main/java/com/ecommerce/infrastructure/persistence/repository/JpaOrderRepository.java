package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaOrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
}
