package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.entity.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderPaymentRepository {

    OrderPayment save(OrderPayment payment);

    Optional<OrderPayment> findById(Long id);

    Optional<OrderPayment> findByOrderId(Long orderId);

    default OrderPayment getByOrderIdOrThrow(Long orderId) {
        return findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다: " + orderId));
    }

    List<OrderPayment> findByStatusAndPaidAtAfter(PaymentStatus status, LocalDateTime after);

    void deleteAll();
}
