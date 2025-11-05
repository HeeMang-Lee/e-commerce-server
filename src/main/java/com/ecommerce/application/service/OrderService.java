package com.ecommerce.application.service;

import com.ecommerce.application.dto.OrderHistoryResponse;
import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
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

    public OrderResponse createOrder(OrderRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        // 2. 재고 확인 및 차감 (동시성 제어)
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findByIdWithLock(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

            product.reduceStock(itemReq.getQuantity());
            productRepository.save(product);

            orderItems.add(new OrderItem(product, itemReq.getQuantity()));
        }

        // 3. 주문 생성
        Order order = new Order(user.getId(), orderItems);
        orderRepository.save(order);

        // 4. 결제 생성
        int totalAmount = order.calculateTotalAmount();
        int discountAmount = 0;
        int usedPoint = request.getUsePoint() != null ? request.getUsePoint() : 0;

        // 쿠폰 적용
        if (request.getUserCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.findById(request.getUserCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));
            userCoupon.use();
            userCouponRepository.save(userCoupon);
            // discountAmount 계산 로직은 추후 확장
        }

        // 포인트 사용
        if (usedPoint > 0) {
            user.deduct(usedPoint);
            userRepository.save(user);
            PointHistory history = new PointHistory(
                    user.getId(),
                    TransactionType.USE,
                    usedPoint,
                    user.getPointBalance(),
                    "주문 결제"
            );
            pointHistoryRepository.save(history);
        }

        OrderPayment payment = new OrderPayment(
                order.getId(),
                totalAmount,
                discountAmount,
                usedPoint,
                request.getUserCouponId()
        );
        payment.complete();
        orderPaymentRepository.save(payment);

        // 5. 판매 이력 기록 (인기 상품 집계용)
        LocalDateTime now = LocalDateTime.now();
        for (OrderItem item : orderItems) {
            popularProductRepository.recordSale(
                    item.getProductId(),
                    item.getQuantity(),
                    now
            );
        }

        // 6. 외부 데이터 플랫폼으로 주문 정보 전송 (직접 호출)
        String orderData = String.format(
                "{\"orderId\":%d,\"orderNumber\":\"%s\",\"userId\":%d,\"totalAmount\":%d,\"finalAmount\":%d}",
                order.getId(), order.getOrderNumber(), user.getId(), totalAmount, payment.getFinalAmount()
        );

        try {
            // 외부 API 직접 호출 시도
            boolean success = dataPlatformService.sendOrderData(orderData);
            if (success) {
                log.info("주문 데이터 전송 성공: orderId={}", order.getId());
            } else {
                // 전송 실패 시 아웃박스에 저장 (재시도용)
                log.warn("주문 데이터 전송 실패, 아웃박스에 저장: orderId={}", order.getId());
                OutboxEvent outboxEvent = new OutboxEvent("ORDER_COMPLETED", orderData);
                outboxEventRepository.save(outboxEvent);
            }
        } catch (Exception e) {
            // 예외 발생 시에도 아웃박스에 저장
            log.error("주문 데이터 전송 중 예외 발생, 아웃박스에 저장: orderId={}", order.getId(), e);
            OutboxEvent outboxEvent = new OutboxEvent("ORDER_COMPLETED", orderData);
            outboxEventRepository.save(outboxEvent);
        }

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
}
