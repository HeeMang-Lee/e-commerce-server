package com.ecommerce.application.service;

import com.ecommerce.application.dto.*;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.domain.service.*;
import com.ecommerce.infrastructure.lock.DistributedLockExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 Application Facade 서비스
 *
 * 책임:
 * - 분산 락 관리 (동시성 제어)
 * - 여러 도메인 서비스의 조율 (Orchestration)
 * - 주문/결제 흐름 관리
 * - DTO 변환
 *
 * 주의:
 * - 비즈니스 로직은 각 Domain Service에 위임
 * - Self-Invocation 해결을 위해 결제 오케스트레이션은 별도 서비스로 분리
 * - 트랜잭션은 Domain Service 레벨에서 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String LOCK_KEY_PREFIX_STOCK = "lock:stock:";
    private static final String LOCK_KEY_PREFIX_PAYMENT = "lock:payment:";

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final UserCouponRepository userCouponRepository;
    private final DistributedLockExecutor lockExecutor;

    private final OrderDomainService orderDomainService;
    private final ProductDomainService productDomainService;
    private final PaymentOrchestrationService paymentOrchestrationService;

    public OrderResponse createOrder(OrderRequest request) {
        User user = userRepository.getByIdOrThrow(request.userId());

        Order order = orderDomainService.createOrder(user.getId());

        List<OrderRequest.OrderItemRequest> sortedItems = request.items().stream()
                .sorted((a, b) -> a.productId().compareTo(b.productId()))
                .toList();

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest itemReq : sortedItems) {
            Product product = reduceStockWithLock(itemReq.productId(), itemReq.quantity());
            OrderItem orderItem = orderDomainService.createOrderItem(order.getId(), product, itemReq.quantity());
            orderItems.add(orderItem);
        }

        int totalAmount = orderItems.stream()
                .mapToInt(OrderItem::getItemTotalAmount)
                .sum();

        int discountAmount = 0;
        if (request.userCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(request.userCouponId());
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

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .toList();

        List<OrderItem> allOrderItems = orderItemRepository.findByOrderIdIn(orderIds);

        var orderItemsMap = allOrderItems.stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderItem::getOrderId));

        return orders.stream()
                .map(order -> {
                    List<OrderItem> items = orderItemsMap.getOrDefault(order.getId(), List.of());
                    return OrderHistoryResponse.from(order, items);
                })
                .toList();
    }

    public OrderHistoryResponse getOrder(Long orderId) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return OrderHistoryResponse.from(order, items);
    }

    private Product reduceStockWithLock(Long productId, int quantity) {
        String lockKey = LOCK_KEY_PREFIX_STOCK + productId;
        return lockExecutor.executeWithLock(lockKey,
                () -> productDomainService.reduceStock(productId, quantity));
    }

    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        String lockKey = LOCK_KEY_PREFIX_PAYMENT + orderId;
        return lockExecutor.executeWithLock(lockKey,
                () -> paymentOrchestrationService.executePayment(orderId, request));
    }
}
