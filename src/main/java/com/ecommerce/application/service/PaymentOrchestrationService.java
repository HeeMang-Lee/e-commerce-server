package com.ecommerce.application.service;

import com.ecommerce.application.dto.PaymentRequest;
import com.ecommerce.application.dto.PaymentResponse;
import com.ecommerce.application.event.PaymentCompletedEvent;
import com.ecommerce.domain.entity.Order;
import com.ecommerce.domain.entity.OrderItem;
import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.repository.OrderItemRepository;
import com.ecommerce.domain.repository.OrderPaymentRepository;
import com.ecommerce.domain.repository.OrderRepository;
import com.ecommerce.domain.service.CouponDomainService;
import com.ecommerce.domain.service.PaymentDomainService;
import com.ecommerce.domain.service.PointDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 오케스트레이션 서비스
 *
 * Self-Invocation 문제를 해결하기 위해 분리된 서비스.
 * @Transactional 메서드 내에서 이벤트를 발행하여 AFTER_COMMIT이 동작하도록 함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrchestrationService {

    private static final String POINT_DESCRIPTION_ORDER_PAYMENT = "주문 결제";

    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final PointDomainService pointDomainService;
    private final CouponDomainService couponDomainService;
    private final PaymentDomainService paymentDomainService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PaymentResponse executePayment(Long orderId, PaymentRequest request) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        int usedPoint = request.usePoint() != null ? request.usePoint() : 0;

        if (usedPoint > 0) {
            pointDomainService.deductPoint(
                    order.getUserId(),
                    usedPoint,
                    POINT_DESCRIPTION_ORDER_PAYMENT,
                    orderId
            );
        }

        if (payment.getUserCouponId() != null) {
            couponDomainService.useCoupon(payment.getUserCouponId());
        }

        OrderPayment completedPayment = paymentDomainService.completePayment(orderId);

        publishPaymentCompletedEvent(order, completedPayment);

        return new PaymentResponse(
                orderId,
                completedPayment.getPaymentStatus().name(),
                completedPayment.getFinalAmount(),
                usedPoint,
                completedPayment.getPaidAt()
        );
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
