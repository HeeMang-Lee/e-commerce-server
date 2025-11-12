package com.ecommerce.application.dto;

import com.ecommerce.domain.entity.Order;

import java.time.LocalDateTime;
import java.util.List;

public record OrderHistoryResponse(
    Long orderId,
    String orderNumber,
    Integer totalAmount,
    LocalDateTime orderDate,
    List<OrderItemInfo> items
) {

    public record OrderItemInfo(
        Long productId,
        String productName,
        Integer quantity,
        Integer price,
        String status
    ) {}

    public static OrderHistoryResponse from(Order order, List<com.ecommerce.domain.entity.OrderItem> orderItems) {
        List<OrderItemInfo> items = orderItems.stream()
                .map(item -> new OrderItemInfo(
                        item.getProductId(),
                        item.getSnapshotProductName(),
                        item.getQuantity(),
                        item.getSnapshotPrice(),
                        item.getStatus().name()
                ))
                .toList();

        int totalAmount = orderItems.stream()
                .mapToInt(com.ecommerce.domain.entity.OrderItem::getItemTotalAmount)
                .sum();

        return new OrderHistoryResponse(
                order.getId(),
                order.getOrderNumber(),
                totalAmount,
                order.getCreatedAt(),
                items
        );
    }
}
