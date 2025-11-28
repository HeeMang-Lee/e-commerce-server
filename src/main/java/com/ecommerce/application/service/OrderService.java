package com.ecommerce.application.service;

import com.ecommerce.application.dto.*;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.domain.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private static final long LOCK_WAIT_TIME_SECONDS = 30;
    private static final long LOCK_LEASE_TIME_SECONDS = 10;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final UserCouponRepository userCouponRepository;
    private final RedissonClient redissonClient;

    // Domain Services
    private final OrderDomainService orderDomainService;
    private final ProductDomainService productDomainService;
    private final PointDomainService pointDomainService;
    private final CouponDomainService couponDomainService;
    private final PaymentDomainService paymentDomainService;

    /**
     * 주문 생성 (Orchestration)
     *
     * 흐름:
     * 1. 주문 생성
     * 2. 재고 차감 (락 적용)
     * 3. 결제 정보 생성
     */
    public OrderResponse createOrder(OrderRequest request) {
        User user = userRepository.getByIdOrThrow(request.userId());

        // 1. 주문 생성 (Domain Service)
        Order order = orderDomainService.createOrder(user.getId());

        // 2. 재고 차감 및 주문 아이템 생성 (데드락 방지를 위해 정렬)
        List<OrderRequest.OrderItemRequest> sortedItems = request.items().stream()
                .sorted((a, b) -> a.productId().compareTo(b.productId()))
                .toList();

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest itemReq : sortedItems) {
            // 분산 락 + 도메인 서비스 조율
            Product product = reduceStockWithLock(itemReq.productId(), itemReq.quantity());
            OrderItem orderItem = orderDomainService.createOrderItem(order.getId(), product, itemReq.quantity());
            orderItems.add(orderItem);
        }

        // 3. 총 금액 계산
        int totalAmount = orderItems.stream()
                .mapToInt(OrderItem::getItemTotalAmount)
                .sum();

        // 4. 할인 금액 (추후 쿠폰 할인 로직 구현 시 사용)
        int discountAmount = 0;
        if (request.userCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(request.userCouponId());
            discountAmount = 0; // TODO: 쿠폰 할인 계산 로직
        }

        // 5. 결제 정보 생성
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

    /**
     * 재고 차감 with 분산 락
     */
    private Product reduceStockWithLock(Long productId, int quantity) {
        String lockKey = LOCK_KEY_PREFIX_STOCK + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("재고 락 획득 실패: productId=" + productId);
            }

            return productDomainService.reduceStock(productId, quantity);

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
     * 재고 복구 with 분산 락 (보상 트랜잭션)
     */
    private void restoreStockWithLock(Long productId, int quantity) {
        String lockKey = LOCK_KEY_PREFIX_STOCK + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("재고 복구 락 획득 실패: productId=" + productId);
            }

            productDomainService.restoreStock(productId, quantity);

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
     * 결제 처리 (Orchestration with Compensation)
     *
     * 흐름:
     * 1. 포인트 차감
     * 2. 쿠폰 사용
     * 3. 결제 완료
     *
     * 실패 시 보상 트랜잭션:
     * - 포인트 롤백 필요 (로그)
     * - 쿠폰 롤백 필요 (로그)
     */
    public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
        String lockKey = LOCK_KEY_PREFIX_PAYMENT + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("결제 처리 락 획득 실패: orderId=" + orderId);
            }

            return executePaymentOrchestration(orderId, request);

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
     * 결제 Orchestration (여러 도메인 서비스 조율)
     */
    private PaymentResponse executePaymentOrchestration(Long orderId, PaymentRequest request) {
        Order order = orderRepository.getByIdOrThrow(orderId);
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        int usedPoint = request.usePoint() != null ? request.usePoint() : 0;
        boolean pointDeducted = false;
        boolean couponUsed = false;

        try {
            // 1. 포인트 차감 (Domain Service)
            if (usedPoint > 0) {
                pointDomainService.deductPoint(
                        order.getUserId(),
                        usedPoint,
                        POINT_DESCRIPTION_ORDER_PAYMENT,
                        orderId
                );
                pointDeducted = true;
            }

            // 2. 쿠폰 사용 (Domain Service)
            if (payment.getUserCouponId() != null) {
                couponDomainService.useCoupon(payment.getUserCouponId());
                couponUsed = true;
            }

            // 3. 결제 완료 (Domain Service)
            paymentDomainService.completePayment(orderId);

            return new PaymentResponse(
                    orderId,
                    payment.getPaymentStatus().name(),
                    payment.getFinalAmount(),
                    usedPoint,
                    payment.getPaidAt()
            );

        } catch (Exception e) {
            log.error("결제 실패, 보상 트랜잭션 시작: orderId={}, error={}", orderId, e.getMessage());

            // 보상 트랜잭션 실행
            executeCompensationTransaction(orderId, order, pointDeducted, usedPoint, couponUsed, payment.getUserCouponId());

            throw e;
        }
    }

    /**
     * 보상 트랜잭션 실행 (재고, 포인트, 쿠폰 복구)
     */
    private void executeCompensationTransaction(Long orderId, Order order, boolean pointDeducted,
                                                 int usedPoint, boolean couponUsed, Long userCouponId) {
        try {
            // 1. 재고 복구
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : orderItems) {
                try {
                    restoreStockWithLock(item.getProductId(), item.getQuantity());
                    log.info("재고 복구 완료: productId={}, quantity={}", item.getProductId(), item.getQuantity());
                } catch (Exception ex) {
                    log.error("재고 복구 실패: productId={}, quantity={}", item.getProductId(), item.getQuantity(), ex);
                }
            }

            // 2. 포인트 복구
            if (pointDeducted) {
                try {
                    pointDomainService.chargePoint(order.getUserId(), usedPoint);
                    log.info("포인트 복구 완료: userId={}, amount={}", order.getUserId(), usedPoint);
                } catch (Exception ex) {
                    log.error("포인트 복구 실패: userId={}, amount={}", order.getUserId(), usedPoint, ex);
                }
            }

            // 3. 쿠폰 복구 (사용 취소)
            if (couponUsed && userCouponId != null) {
                try {
                    couponDomainService.cancelCouponUsage(userCouponId);
                    log.info("쿠폰 복구 완료: userCouponId={}", userCouponId);
                } catch (Exception ex) {
                    log.error("쿠폰 복구 실패: userCouponId={}", userCouponId, ex);
                }
            }

            // 4. 결제 상태를 FAILED로 변경
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
