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
import java.util.stream.Collectors;

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
     * 주문을 생성합니다 (결제는 별도 처리).
     * 1. 재고 차감
     * 2. 주문 생성
     * 3. 쿠폰 할인 금액 계산 (사용은 안 함)
     * 4. OrderPayment를 PENDING 상태로 생성
     */
    public OrderResponse createOrder(OrderRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        // 2. 재고 확인 및 차감 (동시성 제어)
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest itemReq : request.items()) {
            OrderItem orderItem = productRepository.executeWithLock(
                    itemReq.productId(),
                    product -> {
                        // Read -> Modify -> Save가 락 보호 하에 실행됨
                        product.reduceStock(itemReq.quantity());
                        return new OrderItem(product, itemReq.quantity());
                    }
            );
            orderItems.add(orderItem);
        }

        // 3. 주문 생성
        Order order = new Order(user.getId(), orderItems);
        orderRepository.save(order);

        // 4. 할인 금액 계산 (쿠폰 사용은 하지 않음)
        int totalAmount = order.calculateTotalAmount();
        int discountAmount = 0;

        if (request.userCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.findById(request.userCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));
            // TODO: 쿠폰 타입에 따라 할인 금액 계산 (현재는 0)
            discountAmount = 0;
        }

        // 5. 결제를 PENDING 상태로 생성
        int usedPoint = request.usePoint() != null ? request.usePoint() : 0;
        OrderPayment payment = new OrderPayment(
                order.getId(),
                totalAmount,
                discountAmount,
                usedPoint,
                request.userCouponId()
        );
        // complete()를 호출하지 않아 PENDING 상태 유지
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
                .collect(Collectors.toList());
    }

    public OrderHistoryResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다"));
        return OrderHistoryResponse.from(order);
    }

    /**
     * 결제를 처리합니다.
     * 1. 포인트 차감
     * 2. 쿠폰 사용
     * 3. 결제 완료
     * 4. 판매 이력 기록
     * 5. 외부 데이터 전송
     */
    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다"));

        // 2. 사용자 조회
        User user = userRepository.findById(order.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        // 3. 결제 정보 조회
        OrderPayment payment = orderPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다"));

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        try {
            // 4. 포인트 차감
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

            // 5. 쿠폰 사용
            if (payment.getUserCouponId() != null) {
                UserCoupon userCoupon = userCouponRepository.findById(payment.getUserCouponId())
                        .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));
                userCoupon.use();
                userCouponRepository.save(userCoupon);
            }

            // 6. 결제 완료
            payment.complete();
            orderPaymentRepository.save(payment);

            // 7. 판매 이력 기록 (인기 상품 집계용)
            LocalDateTime now = LocalDateTime.now();
            for (OrderItem item : order.getItems()) {
                popularProductRepository.recordSale(
                        item.getProductId(),
                        item.getQuantity(),
                        now
                );
            }

            // 8. 외부 데이터 플랫폼으로 주문 정보 전송
            String orderData = String.format(
                    "{\"orderId\":%d,\"orderNumber\":\"%s\",\"userId\":%d,\"totalAmount\":%d,\"finalAmount\":%d}",
                    order.getId(), order.getOrderNumber(), user.getId(),
                    payment.getOriginalAmount(), payment.getFinalAmount()
            );

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
            // 결제 실패 시 재고 복구
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
