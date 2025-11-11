package com.ecommerce.application.service;

import com.ecommerce.application.dto.*;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.infrastructure.external.DataPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final UserCouponRepository userCouponRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PopularProductRepository popularProductRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DataPlatformService dataPlatformService;

    /**
     * 주문을 생성합니다. 재고를 차감하고 PENDING 상태의 결제 정보를 생성합니다.
     * 실제 결제 처리는 processPayment에서 수행됩니다.
     */
    public OrderResponse createOrder(OrderRequest request) {
        User user = userRepository.getByIdOrThrow(request.userId());

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest itemReq : request.items()) {
            OrderItem orderItem = productRepository.executeWithLock(
                    itemReq.productId(),
                    product -> {
                        product.reduceStock(itemReq.quantity());
                        return new OrderItem(product, itemReq.quantity());
                    }
            );
            orderItems.add(orderItem);
        }

        Order order = new Order(user.getId(), orderItems);
        orderRepository.save(order);

        int totalAmount = order.calculateTotalAmount();
        int discountAmount = 0;

        if (request.userCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(request.userCouponId());
            // TODO: 쿠폰 타입에 따라 할인 금액 계산
            discountAmount = 0;
        }

        int usedPoint = request.usePoint() != null ? request.usePoint() : 0;
        OrderPayment payment = new OrderPayment(
                order.getId(),
                totalAmount,
                discountAmount,
                usedPoint,
                request.userCouponId()
        );
        orderPaymentRepository.save(payment);

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                totalAmount,
                discountAmount,
                usedPoint,
                payment.getFinalAmount()
        );
    }

    public List<OrderHistoryResponse> getOrderHistory(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream()
                .map(OrderHistoryResponse::from)
                .toList();
    }

    public OrderHistoryResponse getOrder(Long orderId) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        return OrderHistoryResponse.from(order);
    }

    /**
     * 결제를 처리합니다.
     * 포인트/쿠폰 사용, 결제 완료, 판매 이력 기록, 외부 데이터 전송을 수행하며
     * 실패 시 재고를 복구합니다.
     */
    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        User user = userRepository.getByIdOrThrow(order.getUserId());
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        try {
            int usedPoint = request.usePoint() != null ? request.usePoint() : 0;
            if (usedPoint > 0) {
                user.deduct(usedPoint);
                userRepository.save(user);
                PointHistory history = new PointHistory(
                        user.getId(),
                        TransactionType.USE,
                        usedPoint,
                        user.getPointBalance(),
                        "주문 결제",
                        orderId
                );
                pointHistoryRepository.save(history);
            }

            if (payment.getUserCouponId() != null) {
                UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(payment.getUserCouponId());
                userCoupon.use();
                userCouponRepository.save(userCoupon);
            }

            payment.complete();
            orderPaymentRepository.save(payment);

            LocalDateTime now = LocalDateTime.now();
            for (OrderItem item : order.getItems()) {
                popularProductRepository.recordSale(
                        item.getProductId(),
                        item.getQuantity(),
                        now
                );
            }

            String orderData = "{\"orderId\":" + order.getId() +
                    ",\"orderNumber\":\"" + order.getOrderNumber() +
                    "\",\"userId\":" + user.getId() +
                    ",\"totalAmount\":" + payment.getOriginalAmount() +
                    ",\"finalAmount\":" + payment.getFinalAmount() + "}";

            // 외부 전송 실패 시 아웃박스 패턴으로 재시도 보장
            try {
                boolean success = dataPlatformService.sendOrderData(orderData);
                if (success) {
                    log.info("주문 데이터 전송 성공: orderId={}", order.getId());
                } else {
                    log.warn("주문 데이터 전송 실패, 아웃박스에 저장: orderId={}", order.getId());
                    OutboxEvent outboxEvent = new OutboxEvent("ORDER_COMPLETED", orderData);
                    outboxEventRepository.save(outboxEvent);
                }
            } catch (Exception e) {
                log.error("주문 데이터 전송 중 예외 발생, 아웃박스에 저장: orderId={}", order.getId(), e);
                OutboxEvent outboxEvent = new OutboxEvent("ORDER_COMPLETED", orderData);
                outboxEventRepository.save(outboxEvent);
            }

            return new PaymentResponse(
                    orderId,
                    payment.getPaymentStatus().name(),
                    payment.getFinalAmount(),
                    usedPoint,
                    payment.getPaidAt()
            );

        } catch (Exception e) {
            log.error("결제 처리 중 오류 발생, 재고 복구: orderId={}", orderId, e);
            for (OrderItem item : order.getItems()) {
                productRepository.executeWithLock(
                        item.getProductId(),
                        product -> {
                            product.restoreStock(item.getQuantity());
                            return null;
                        }
                );
            }
            payment.fail();
            orderPaymentRepository.save(payment);
            throw e;
        }
    }
}
