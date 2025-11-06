package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.repository.OrderPaymentRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InMemory OrderPayment Repository 구현
 */
@Repository
public class InMemoryOrderPaymentRepository implements OrderPaymentRepository {

    private final Map<Long, OrderPayment> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderPayment save(OrderPayment payment) {
        if (payment.getId() == null) {
            payment.setId(idGenerator.getAndIncrement());
        }
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<OrderPayment> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<OrderPayment> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(payment -> payment.getOrderId().equals(orderId))
                .findFirst();
    }
}
