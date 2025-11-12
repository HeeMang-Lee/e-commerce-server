package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.OrderItem;

import java.util.List;

public interface OrderItemRepository {

    OrderItem save(OrderItem orderItem);

    List<OrderItem> saveAll(List<OrderItem> orderItems);

    List<OrderItem> findByOrderId(Long orderId);
}
