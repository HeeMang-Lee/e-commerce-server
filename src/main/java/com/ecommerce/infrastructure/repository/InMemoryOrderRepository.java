package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.Order;
import com.ecommerce.domain.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * InMemory Order Repository 구현
 */
@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            order.setId(idGenerator.getAndIncrement());
        }
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return store.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .collect(Collectors.toList());
    }
}
