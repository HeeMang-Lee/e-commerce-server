package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.OrderPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaOrderPaymentRepository extends JpaRepository<OrderPayment, Long> {
    Optional<OrderPayment> findByOrderId(Long orderId);
}
