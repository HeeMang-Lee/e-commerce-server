package com.ecommerce.domain.service;

import com.ecommerce.domain.entity.Order;
import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.entity.PaymentStatus;
import com.ecommerce.domain.repository.OrderPaymentRepository;
import com.ecommerce.domain.repository.OrderRepository;
import com.ecommerce.domain.repository.OutboxEventRepository;
import com.ecommerce.infrastructure.external.DataPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 도메인 서비스
 *
 * 책임:
 * - 결제 완료 처리
 * - 주문 데이터 전송 (아웃박스 패턴)
 * - 트랜잭션 경계 관리
 *
 * 주의:
 * - 포인트/쿠폰 차감은 상위 Facade에서 처리
 * - 다른 도메인 서비스에 의존하지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentDomainService {

    private static final String EVENT_TYPE_ORDER_COMPLETED = "ORDER_COMPLETED";

    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DataPlatformService dataPlatformService;

    /**
     * 결제 완료 처리
     *
     * @param orderId 주문 ID
     * @return 완료된 결제
     */
    @Transactional
    public OrderPayment completePayment(Long orderId) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        payment.complete();
        orderPaymentRepository.save(payment);

        // 주문 데이터 전송 (Best Effort + Outbox Pattern)
        sendOrderDataWithOutbox(order, payment);

        return payment;
    }

    /**
     * 결제 실패 처리 (보상 트랜잭션)
     *
     * @param orderId 주문 ID
     * @return 실패 처리된 결제
     */
    @Transactional
    public OrderPayment failPayment(Long orderId) {
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            log.warn("이미 완료된 결제를 실패로 변경할 수 없습니다: orderId={}", orderId);
            return payment;
        }

        payment.fail();
        orderPaymentRepository.save(payment);
        log.info("결제 상태 FAILED로 변경: orderId={}", orderId);

        return payment;
    }

    /**
     * 주문 데이터 전송 (아웃박스 패턴 적용)
     */
    private void sendOrderDataWithOutbox(Order order, OrderPayment payment) {
        String orderData = buildOrderData(order, payment);

        try {
            boolean success = dataPlatformService.sendOrderData(orderData);
            if (success) {
                log.info("주문 데이터 전송 성공: orderId={}", order.getId());
            } else {
                log.warn("주문 데이터 전송 실패, 아웃박스에 저장: orderId={}", order.getId());
                saveToOutbox(orderData);
            }
        } catch (Exception e) {
            log.error("주문 데이터 전송 중 예외 발생, 아웃박스에 저장: orderId={}", order.getId(), e);
            saveToOutbox(orderData);
        }
    }

    private String buildOrderData(Order order, OrderPayment payment) {
        return "{\"orderId\":" + order.getId() +
                ",\"orderNumber\":\"" + order.getOrderNumber() +
                "\",\"userId\":" + order.getUserId() +
                ",\"totalAmount\":" + payment.getOriginalAmount() +
                ",\"finalAmount\":" + payment.getFinalAmount() + "}";
    }

    private void saveToOutbox(String orderData) {
        OutboxEvent outboxEvent = new OutboxEvent(EVENT_TYPE_ORDER_COMPLETED, orderData);
        outboxEventRepository.save(outboxEvent);
    }
}
