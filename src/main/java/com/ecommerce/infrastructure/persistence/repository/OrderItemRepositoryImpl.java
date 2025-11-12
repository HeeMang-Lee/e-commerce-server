package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.OrderItem;
import com.ecommerce.domain.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {

    private final JpaOrderItemRepository jpaOrderItemRepository;

    @Override
    public OrderItem save(OrderItem orderItem) {
        return jpaOrderItemRepository.save(orderItem);
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        return jpaOrderItemRepository.saveAll(orderItems);
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return jpaOrderItemRepository.findByOrderId(orderId);
    }

    @Override
    public void deleteAll() {
        jpaOrderItemRepository.deleteAll();
    }
}
