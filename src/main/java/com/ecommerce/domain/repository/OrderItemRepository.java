package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.OrderItem;

import java.util.List;

public interface OrderItemRepository {

    OrderItem save(OrderItem orderItem);

    List<OrderItem> saveAll(List<OrderItem> orderItems);

    List<OrderItem> findByOrderId(Long orderId);

    /**
     * 여러 주문의 주문 아이템을 한 번에 조회합니다 (N+1 문제 해결)
     */
    List<OrderItem> findByOrderIdIn(List<Long> orderIds);

    void deleteAll();
}
