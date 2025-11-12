package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.OrderItem;
import com.ecommerce.domain.repository.OrderItemRepository;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryOrderItemRepository implements OrderItemRepository {

    private final Map<Long, OrderItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem.getId() == null) {
            orderItem.setId(idGenerator.getAndIncrement());
        }
        store.put(orderItem.getId(), orderItem);
        return orderItem;
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(this::save)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(item -> Objects.equals(item.getOrderId(), orderId))
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }
}
