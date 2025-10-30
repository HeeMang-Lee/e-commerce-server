package com.ecommerce.controller;

import com.ecommerce.api.OrderApi;
import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.OrderDto;
import com.ecommerce.dto.ResponseCode;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
public class OrderController implements OrderApi {

    @Override
    public ApiResponse<OrderDto.Response> createOrder(OrderDto.CreateRequest request) {
        // 예시: 재고 부족 시 예외 발생
        // throw new BusinessException(ResponseCode.PRODUCT_OUT_OF_STOCK);

        List<OrderDto.OrderItemResponse> items = Arrays.asList(
                new OrderDto.OrderItemResponse(1L, "무선 키보드", 2, 89000, 178000)
        );

        OrderDto.Response order = new OrderDto.Response(
                100L,
                "ORD-20241029-001",
                request.getUserId(),
                items,
                178000,
                5000,
                173000,
                "PENDING",
                LocalDateTime.now()
        );

        return ApiResponse.of(ResponseCode.ORDER_CREATED, order);
    }

    @Override
    public ApiResponse<OrderDto.Response> getOrder(Long orderId) {
        // 예시: 주문이 없는 경우 예외 발생
        // throw new BusinessException(ResponseCode.ORDER_NOT_FOUND);

        List<OrderDto.OrderItemResponse> items = Arrays.asList(
                new OrderDto.OrderItemResponse(1L, "무선 키보드", 2, 89000, 178000)
        );

        OrderDto.Response order = new OrderDto.Response(
                orderId,
                "ORD-20241029-001",
                1L,
                items,
                178000,
                5000,
                173000,
                "PENDING",
                LocalDateTime.now().minusHours(1)
        );

        return ApiResponse.of(ResponseCode.ORDER_SUCCESS, order);
    }

    @Override
    public ApiResponse<List<OrderDto.Response>> getUserOrders(Long userId) {
        List<OrderDto.OrderItemResponse> items1 = Arrays.asList(
                new OrderDto.OrderItemResponse(1L, "무선 키보드", 2, 89000, 178000)
        );

        List<OrderDto.OrderItemResponse> items2 = Arrays.asList(
                new OrderDto.OrderItemResponse(2L, "무선 마우스", 1, 45000, 45000)
        );

        List<OrderDto.Response> orders = Arrays.asList(
                new OrderDto.Response(100L, "ORD-20241029-001", userId, items1, 178000, 5000, 173000, "COMPLETED", LocalDateTime.now().minusDays(1)),
                new OrderDto.Response(101L, "ORD-20241029-002", userId, items2, 45000, 0, 45000, "PENDING", LocalDateTime.now())
        );

        return ApiResponse.of(ResponseCode.ORDER_SUCCESS, orders);
    }

    @Override
    public ApiResponse<OrderDto.PaymentResponse> processPayment(Long orderId, OrderDto.PaymentRequest request) {
        // 예시: 이미 결제된 주문인 경우 예외 발생
        // throw new BusinessException(ResponseCode.ORDER_ALREADY_PAID);

        // 예시: 포인트 부족 시 예외 발생
        // throw new BusinessException(ResponseCode.POINT_INSUFFICIENT);

        OrderDto.PaymentResponse payment = new OrderDto.PaymentResponse(
                orderId,
                "COMPLETED",
                163000,
                request.getUsePoint() != null ? request.getUsePoint() : 0,
                LocalDateTime.now()
        );

        return ApiResponse.of(ResponseCode.ORDER_PAYMENT_SUCCESS, payment);
    }
}
