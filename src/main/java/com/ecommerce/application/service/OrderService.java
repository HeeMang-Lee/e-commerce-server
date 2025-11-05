package com.ecommerce.application.service;

import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final UserCouponRepository userCouponRepository;
    private final PointHistoryRepository pointHistoryRepository;

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

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                totalAmount,
                discountAmount,
                usedPoint,
                payment.getFinalAmount()
        );
    }
}
