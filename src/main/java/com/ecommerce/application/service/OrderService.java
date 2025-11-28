package com.ecommerce.application.service;

import com.ecommerce.application.dto.*;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.infrastructure.external.DataPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String EVENT_TYPE_ORDER_COMPLETED = "ORDER_COMPLETED";
    private static final String POINT_DESCRIPTION_ORDER_PAYMENT = "주문 결제";
    private static final String LOCK_KEY_PREFIX_STOCK = "lock:stock:";
    private static final String LOCK_KEY_PREFIX_PAYMENT = "lock:payment:";
    private static final long LOCK_WAIT_TIME_SECONDS = 30;
    private static final long LOCK_LEASE_TIME_SECONDS = 10;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final UserCouponRepository userCouponRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DataPlatformService dataPlatformService;
    private final RedissonClient redissonClient;
    private final PointService pointService;
    private final CouponService couponService;

    public OrderResponse createOrder(OrderRequest request) {
        User user = userRepository.getByIdOrThrow(request.userId());

        Order order = new Order(user.getId());
        orderRepository.save(order);
        order.assignOrderNumber();
        orderRepository.save(order);

        List<OrderRequest.OrderItemRequest> sortedItems = request.items().stream()
                .sorted((a, b) -> a.productId().compareTo(b.productId()))
                .toList();

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest itemReq : sortedItems) {
            OrderItem orderItem = reduceStockWithLock(order.getId(), itemReq.productId(), itemReq.quantity());
            orderItems.add(orderItem);
        }
        orderItemRepository.saveAll(orderItems);

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

    private OrderItem reduceStockWithLock(Long orderId, Long productId, int quantity) {
        String lockKey = LOCK_KEY_PREFIX_STOCK + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("재고 락 획득 실패: productId=" + productId);
            }

            return executeStockReduction(orderId, productId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public OrderItem executeStockReduction(Long orderId, Long productId, int quantity) {
        Product product = productRepository.getByIdOrThrow(productId);
        product.reduceStock(quantity);
        productRepository.save(product);

        OrderItem orderItem = new OrderItem(product, quantity);
        orderItem.setOrderId(orderId);
        return orderItem;
    }

    private void restoreStockWithLock(Long productId, int quantity) {
        String lockKey = LOCK_KEY_PREFIX_STOCK + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("재고 복구 락 획득 실패: productId=" + productId);
            }

            executeStockRestore(productId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public void executeStockRestore(Long productId, int quantity) {
        Product product = productRepository.getByIdOrThrow(productId);
        product.restoreStock(quantity);
        productRepository.save(product);
    }

    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        String lockKey = LOCK_KEY_PREFIX_PAYMENT + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("결제 처리 락 획득 실패: orderId=" + orderId);
            }

            return executePayment(orderId, request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public PaymentResponse executePayment(Long orderId, PaymentRequest request) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        int usedPoint = request.usePoint() != null ? request.usePoint() : 0;
        boolean pointDeducted = false;
        boolean couponUsed = false;

        try {
            if (usedPoint > 0) {
                pointService.deductPoint(order.getUserId(), usedPoint, POINT_DESCRIPTION_ORDER_PAYMENT, orderId);
                pointDeducted = true;
            }

            if (payment.getUserCouponId() != null) {
                couponService.useCoupon(payment.getUserCouponId());
                couponUsed = true;
            }

            payment.complete();
            orderPaymentRepository.save(payment);

            String orderData = "{\"orderId\":" + order.getId() +
                    ",\"orderNumber\":\"" + order.getOrderNumber() +
                    "\",\"userId\":" + order.getUserId() +
                    ",\"totalAmount\":" + payment.getOriginalAmount() +
                    ",\"finalAmount\":" + payment.getFinalAmount() + "}";

            try {
                boolean success = dataPlatformService.sendOrderData(orderData);
                if (success) {
                    log.info("주문 데이터 전송 성공: orderId={}", order.getId());
                } else {
                    log.warn("주문 데이터 전송 실패, 아웃박스에 저장: orderId={}", order.getId());
                    OutboxEvent outboxEvent = new OutboxEvent(EVENT_TYPE_ORDER_COMPLETED, orderData);
                    outboxEventRepository.save(outboxEvent);
                }
            } catch (Exception e) {
                log.error("주문 데이터 전송 중 예외 발생, 아웃박스에 저장: orderId={}", order.getId(), e);
                OutboxEvent outboxEvent = new OutboxEvent(EVENT_TYPE_ORDER_COMPLETED, orderData);
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
            log.error("결제 처리 중 오류 발생, 보상 트랜잭션 시작: orderId={}", orderId, e);

            List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : orderItems) {
                restoreStockWithLock(item.getProductId(), item.getQuantity());
            }

            if (pointDeducted) {
                pointService.chargePoint(new PointChargeRequest(order.getUserId(), usedPoint));
                log.info("포인트 복구 완료: userId={}, amount={}", order.getUserId(), usedPoint);
            }

            if (couponUsed && payment.getUserCouponId() != null) {
                UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(payment.getUserCouponId());
                userCoupon.restore();
                userCouponRepository.save(userCoupon);
                log.info("쿠폰 복구 완료: userCouponId={}", userCoupon.getId());
            }

            payment.fail();
            orderPaymentRepository.save(payment);
            log.info("보상 트랜잭션 완료: orderId={}", orderId);
            throw e;
        }
    }
}
