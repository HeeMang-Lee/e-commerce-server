package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.*;
import com.ecommerce.application.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public OrderResponse createOrder(@RequestBody @Valid OrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/users/{userId}")
    public List<OrderHistoryResponse> getOrderHistory(@PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") Long userId) {
        return orderService.getOrderHistory(userId);
    }

    @GetMapping("/{orderId}")
    public OrderHistoryResponse getOrder(@PathVariable @Positive(message = "주문 ID는 양수여야 합니다") Long orderId) {
        return orderService.getOrder(orderId);
    }

    @PostMapping("/{orderId}/payment")
    public PaymentResponse processPayment(
            @PathVariable @Positive(message = "주문 ID는 양수여야 합니다") Long orderId,
            @RequestBody @Valid PaymentRequest request) {
        return orderService.processPayment(orderId, request);
    }
}
