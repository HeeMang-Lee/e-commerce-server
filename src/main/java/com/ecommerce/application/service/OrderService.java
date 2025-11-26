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
    private static final String LOCK_KEY_PREFIX = "ecommerce:lock:product:stock:";

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

    /**
     * 주문을 생성합니다. 재고를 차감하고 PENDING 상태의 결제 정보를 생성합니다.
     * 실제 결제 처리는 processPayment에서 수행됩니다.
     */
    public OrderResponse createOrder(OrderRequest request) {
        User user = userRepository.getByIdOrThrow(request.userId());

        Order order = new Order(user.getId());
        orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest itemReq : request.items()) {
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

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .toList();

        // IN 절로 모든 주문 아이템을 한 번에 조회 (N+1 문제 해결)
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

    /**
     * Redis 분산 락을 사용한 재고 차감 및 주문 아이템 생성
     */
    private OrderItem reduceStockWithLock(Long orderId, Long productId, int quantity) {
        String lockKey = LOCK_KEY_PREFIX + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
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

    /**
     * 재고 차감 트랜잭션 처리 (락 획득 후 실행)
     */
    @Transactional
    private OrderItem executeStockReduction(Long orderId, Long productId, int quantity) {
        Product product = productRepository.getByIdOrThrow(productId);
        product.reduceStock(quantity);
        productRepository.save(product);

        OrderItem orderItem = new OrderItem(product, quantity);
        orderItem.setOrderId(orderId);
        return orderItem;
    }

    /**
     * Redis 분산 락을 사용한 재고 복구 (보상 트랜잭션)
     */
    private void restoreStockWithLock(Long productId, int quantity) {
        String lockKey = LOCK_KEY_PREFIX + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
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

    /**
     * 재고 복구 트랜잭션 처리 (락 획득 후 실행)
     */
    @Transactional
    private void executeStockRestore(Long productId, int quantity) {
        Product product = productRepository.getByIdOrThrow(productId);
        product.restoreStock(quantity);
        productRepository.save(product);
    }

    /**
     * 결제를 처리합니다.
     * 포인트/쿠폰 사용, 결제 완료, 판매 이력 기록, 외부 데이터 전송을 수행하며
     * 실패 시 재고, 포인트, 쿠폰을 복구합니다.
     */
    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        User user = userRepository.getByIdOrThrow(order.getUserId());
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        int usedPoint = request.usePoint() != null ? request.usePoint() : 0;
        boolean pointDeducted = false;
        boolean couponUsed = false;

        try {
            if (usedPoint > 0) {
                user.deduct(usedPoint);
                userRepository.save(user);
                PointHistory history = new PointHistory(
                        user.getId(),
                        TransactionType.USE,
                        usedPoint,
                        user.getPointBalance(),
                        POINT_DESCRIPTION_ORDER_PAYMENT,
                        orderId
                );
                pointHistoryRepository.save(history);
                pointDeducted = true;
            }

            if (payment.getUserCouponId() != null) {
                UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(payment.getUserCouponId());
                userCoupon.use();
                userCouponRepository.save(userCoupon);
                couponUsed = true;
            }

            payment.complete();
            orderPaymentRepository.save(payment);

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

            // 1. 재고 복구
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : orderItems) {
                restoreStockWithLock(item.getProductId(), item.getQuantity());
            }

            // 2. 포인트 복구
            if (pointDeducted) {
                User userToRestore = userRepository.getByIdOrThrow(order.getUserId());
                userToRestore.charge(usedPoint);
                userRepository.save(userToRestore);
                PointHistory restoreHistory = new PointHistory(
                        userToRestore.getId(),
                        TransactionType.REFUND,
                        usedPoint,
                        userToRestore.getPointBalance(),
                        "결제 실패로 인한 포인트 환불",
                        orderId
                );
                pointHistoryRepository.save(restoreHistory);
                log.info("포인트 복구 완료: userId={}, amount={}", userToRestore.getId(), usedPoint);
            }

            // 3. 쿠폰 복구
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
