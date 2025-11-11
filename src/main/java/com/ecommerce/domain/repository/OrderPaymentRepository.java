package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.OrderPayment;

import java.util.Optional;

public interface OrderPaymentRepository {

    OrderPayment save(OrderPayment payment);

    Optional<OrderPayment> findById(Long id);

    Optional<OrderPayment> findByOrderId(Long orderId);

    default OrderPayment getByOrderIdOrThrow(Long orderId) {
        return findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다: " + orderId));
    }
}
