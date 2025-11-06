package com.ecommerce.application.dto;

import com.ecommerce.domain.entity.Order;
import com.ecommerce.domain.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class OrderHistoryResponse {
    private Long orderId;
    private String orderNumber;
    private Integer totalAmount;
    private LocalDateTime orderDate;
    private List<OrderItemInfo> items;

    @Getter
    @AllArgsConstructor
    public static class OrderItemInfo {
        private Long productId;
        private String productName;
        private Integer quantity;
        private Integer price;
        private String status;
    }

    public static OrderHistoryResponse from(Order order) {
        List<OrderItemInfo> items = order.getItems().stream()
                .map(item -> new OrderItemInfo(
                        item.getProductId(),
                        item.getSnapshotProductName(),
                        item.getQuantity(),
                        item.getSnapshotPrice(),
                        item.getStatus().name()
                ))
                .collect(Collectors.toList());

        return new OrderHistoryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.calculateTotalAmount(),
                order.getCreatedAt(),
                items
        );
    }
}
