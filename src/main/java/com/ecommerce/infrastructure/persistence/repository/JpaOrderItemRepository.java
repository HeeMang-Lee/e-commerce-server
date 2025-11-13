package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaOrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> findByOrderIdIn(List<Long> orderIds);
}
