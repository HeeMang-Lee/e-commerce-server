package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    default Order getByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + id));
    }

    List<Order> findByUserId(Long userId);
}
