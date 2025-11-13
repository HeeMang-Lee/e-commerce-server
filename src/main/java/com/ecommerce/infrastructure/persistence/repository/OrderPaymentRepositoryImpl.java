package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.repository.OrderPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderPaymentRepositoryImpl implements OrderPaymentRepository {

    private final JpaOrderPaymentRepository jpaOrderPaymentRepository;

    @Override
    public OrderPayment save(OrderPayment payment) {
        return jpaOrderPaymentRepository.save(payment);
    }

    @Override
    public Optional<OrderPayment> findById(Long id) {
        return jpaOrderPaymentRepository.findById(id);
    }

    @Override
    public Optional<OrderPayment> findByOrderId(Long orderId) {
        return jpaOrderPaymentRepository.findByOrderId(orderId);
    }

    @Override
    public void deleteAll() {
        jpaOrderPaymentRepository.deleteAll();
    }
}
