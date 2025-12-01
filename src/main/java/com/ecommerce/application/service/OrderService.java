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
 * - Self-Invocation 없음
 * - 트랜잭션은 Domain Service 레벨에서 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String LOCK_KEY_PREFIX_STOCK = "lock:stock:";
    private static final String LOCK_KEY_PREFIX_PAYMENT = "lock:payment:";
    private static final String POINT_DESCRIPTION_ORDER_PAYMENT = "주문 결제";

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final UserCouponRepository userCouponRepository;
    private final DistributedLockExecutor lockExecutor;

    private final OrderDomainService orderDomainService;
    private final ProductDomainService productDomainService;
    private final PointDomainService pointDomainService;
    private final CouponDomainService couponDomainService;
    private final PaymentDomainService paymentDomainService;

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

    private void restoreStockWithLock(Long productId, int quantity) {
        String lockKey = LOCK_KEY_PREFIX_STOCK + productId;
        lockExecutor.executeWithLock(lockKey,
                () -> productDomainService.restoreStock(productId, quantity));
    }

    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        String lockKey = LOCK_KEY_PREFIX_PAYMENT + orderId;
        return lockExecutor.executeWithLock(lockKey,
                () -> executePaymentOrchestration(orderId, request));
    }

    private PaymentResponse executePaymentOrchestration(Long orderId, PaymentRequest request) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        int usedPoint = request.usePoint() != null ? request.usePoint() : 0;
        boolean pointDeducted = false;
        boolean couponUsed = false;

        try {
            if (usedPoint > 0) {
                pointDomainService.deductPoint(
                        order.getUserId(),
                        usedPoint,
                        POINT_DESCRIPTION_ORDER_PAYMENT,
                        orderId
                );
                pointDeducted = true;
            }

            if (payment.getUserCouponId() != null) {
                couponDomainService.useCoupon(payment.getUserCouponId());
                couponUsed = true;
            }

            OrderPayment completedPayment = paymentDomainService.completePayment(orderId);

            return new PaymentResponse(
                    orderId,
                    completedPayment.getPaymentStatus().name(),
                    completedPayment.getFinalAmount(),
                    usedPoint,
                    completedPayment.getPaidAt()
            );

        } catch (Exception e) {
            log.error("결제 실패, 보상 트랜잭션 시작: orderId={}, error={}", orderId, e.getMessage());
            executeCompensationTransaction(orderId, order, pointDeducted, usedPoint, couponUsed, payment.getUserCouponId());
            throw e;
        }
    }

    private void executeCompensationTransaction(Long orderId, Order order, boolean pointDeducted,
                                                 int usedPoint, boolean couponUsed, Long userCouponId) {
        try {
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : orderItems) {
                try {
                    restoreStockWithLock(item.getProductId(), item.getQuantity());
                    log.info("재고 복구 완료: productId={}, quantity={}", item.getProductId(), item.getQuantity());
                } catch (Exception ex) {
                    log.error("재고 복구 실패: productId={}, quantity={}", item.getProductId(), item.getQuantity(), ex);
                }
            }

            if (pointDeducted) {
                try {
                    pointDomainService.chargePoint(order.getUserId(), usedPoint);
                    log.info("포인트 복구 완료: userId={}, amount={}", order.getUserId(), usedPoint);
                } catch (Exception ex) {
                    log.error("포인트 복구 실패: userId={}, amount={}", order.getUserId(), usedPoint, ex);
                }
            }

            if (couponUsed && userCouponId != null) {
                try {
                    couponDomainService.cancelCouponUsage(userCouponId);
                    log.info("쿠폰 복구 완료: userCouponId={}", userCouponId);
                } catch (Exception ex) {
                    log.error("쿠폰 복구 실패: userCouponId={}", userCouponId, ex);
                }
            }

            try {
                paymentDomainService.failPayment(orderId);
                log.info("결제 상태 FAILED로 변경: orderId={}", orderId);
            } catch (Exception ex) {
                log.error("결제 상태 변경 실패: orderId={}", orderId, ex);
            }

        } catch (Exception e) {
            log.error("보상 트랜잭션 중 오류 발생: orderId={}", orderId, e);
        }
    }

}
