package com.ecommerce.domain.service;

import com.ecommerce.domain.entity.Order;
import com.ecommerce.domain.entity.OrderItem;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.OrderItemRepository;
import com.ecommerce.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 도메인 서비스
 *
 * 책임:
 * - 주문 생성의 핵심 비즈니스 로직
 * - 주문 아이템 관리
 * - 트랜잭션 경계 관리
 *
 * 주의:
 * - 다른 도메인 서비스에 의존하지 않음
 * - 재고 차감, 결제 등은 상위 Facade에서 조율
 */
@Service
@RequiredArgsConstructor
public class OrderDomainService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public Order createOrder(Long userId) {
        Order order = new Order(userId);
        orderRepository.save(order);
        order.assignOrderNumber();
        orderRepository.save(order);
        return order;
    }

    @Transactional
    public OrderItem createOrderItem(Long orderId, Product product, int quantity) {
        OrderItem orderItem = new OrderItem(product, quantity);
        orderItem.setOrderId(orderId);
        orderItemRepository.save(orderItem);
        return orderItem;
    }
}
