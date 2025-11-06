package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.OrderPayment;

import java.util.Optional;

/**
 * 주문 결제 Repository 인터페이스
 */
public interface OrderPaymentRepository {

    /**
     * 주문 결제를 저장합니다.
     *
     * @param payment 저장할 주문 결제
     * @return 저장된 주문 결제
     */
    OrderPayment save(OrderPayment payment);

    /**
     * ID로 주문 결제를 조회합니다.
     *
     * @param id 주문 결제 ID
     * @return 주문 결제 Optional
     */
    Optional<OrderPayment> findById(Long id);

    /**
     * 주문 ID로 결제 정보를 조회합니다.
     *
     * @param orderId 주문 ID
     * @return 주문 결제 Optional
     */
    Optional<OrderPayment> findByOrderId(Long orderId);
}
