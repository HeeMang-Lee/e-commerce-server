package com.ecommerce.domain.service;

import com.ecommerce.application.event.PaymentCompletedEvent;
import com.ecommerce.domain.entity.Order;
import com.ecommerce.domain.entity.OrderItem;
import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.entity.PaymentStatus;
import com.ecommerce.domain.repository.OrderItemRepository;
import com.ecommerce.domain.repository.OrderPaymentRepository;
import com.ecommerce.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 도메인 서비스 (Choreography 방식)
 *
 * 책임:
 * - 결제 완료/실패 처리
 * - 결제 완료 이벤트 발행 (트랜잭션 내에서)
 *
 * Choreography 패턴:
 * - 도메인 서비스가 직접 이벤트를 발행
 * - 이벤트 핸들러들이 독립적으로 반응
 * - 중앙 조율자(Orchestrator) 없음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentDomainService {

    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderPayment completePayment(Long orderId) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        payment.complete();
        orderPaymentRepository.save(payment);

        // Choreography: 도메인 서비스에서 직접 이벤트 발행
        publishPaymentCompletedEvent(order, payment);

        return payment;
    }

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

    private void publishPaymentCompletedEvent(Order order, OrderPayment payment) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        List<PaymentCompletedEvent.OrderItemInfo> itemInfos = orderItems.stream()
                .map(item -> new PaymentCompletedEvent.OrderItemInfo(
                        item.getProductId(),
                        item.getQuantity()))
                .toList();

        PaymentCompletedEvent event = new PaymentCompletedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                payment.getOriginalAmount(),
                payment.getFinalAmount(),
                payment.getPaidAt(),
                itemInfos
        );

        eventPublisher.publishEvent(event);
        log.debug("결제 완료 이벤트 발행: orderId={}", order.getId());
    }
}
