package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaOrderPaymentRepository extends JpaRepository<OrderPayment, Long> {
    Optional<OrderPayment> findByOrderId(Long orderId);

    List<OrderPayment> findByPaymentStatusAndPaidAtAfter(PaymentStatus status, LocalDateTime after);
}
