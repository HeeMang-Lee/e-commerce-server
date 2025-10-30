package com.ecommerce.api;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.OrderDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Order", description = "주문 API")
@RequestMapping("/api/orders")
public interface OrderApi {

    @Operation(summary = "주문 생성", description = "새로운 주문을 생성합니다.")
    @PostMapping
    ApiResponse<OrderDto.Response> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "주문 생성 요청", required = true)
            @RequestBody OrderDto.CreateRequest request
    );

    @Operation(summary = "주문 조회", description = "특정 주문의 상세 정보를 조회합니다.")
    @GetMapping("/{orderId}")
    ApiResponse<OrderDto.Response> getOrder(
            @Parameter(description = "주문 ID", required = true, example = "100")
            @PathVariable Long orderId
    );

    @Operation(summary = "사용자 주문 목록 조회", description = "특정 사용자의 주문 목록을 조회합니다.")
    @GetMapping("/users/{userId}")
    ApiResponse<List<OrderDto.Response>> getUserOrders(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId
    );

    @Operation(summary = "결제 처리", description = "주문에 대한 결제를 처리합니다.")
    @PostMapping("/{orderId}/payment")
    ApiResponse<OrderDto.PaymentResponse> processPayment(
            @Parameter(description = "주문 ID", required = true, example = "100")
            @PathVariable Long orderId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "결제 요청", required = true)
            @RequestBody OrderDto.PaymentRequest request
    );
}
