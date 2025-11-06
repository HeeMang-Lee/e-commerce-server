package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.*;
import com.ecommerce.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/users/{userId}")
    public List<OrderHistoryResponse> getOrderHistory(@PathVariable Long userId) {
        return orderService.getOrderHistory(userId);
    }

    @GetMapping("/{orderId}")
    public OrderHistoryResponse getOrder(@PathVariable Long orderId) {
        return orderService.getOrder(orderId);
    }

    @PostMapping("/{orderId}/payment")
    public PaymentResponse processPayment(
            @PathVariable Long orderId,
            @RequestBody PaymentRequest request) {
        return orderService.processPayment(orderId, request);
    }
}
