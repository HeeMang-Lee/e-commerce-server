package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "주문 응답")
public record OrderResponse(
    @Schema(description = "주문 ID", example = "100")
    Long orderId,

    @Schema(description = "주문 번호", example = "ORD-20241029-001")
    String orderNumber,

    @Schema(description = "사용자 ID", example = "1")
    Long userId,

    @Schema(description = "주문 상품 목록")
    List<OrderItemResponse> items,

    @Schema(description = "총 금액", example = "178000")
    Integer totalAmount,

    @Schema(description = "할인 금액", example = "5000")
    Integer discountAmount,

    @Schema(description = "최종 결제 금액", example = "173000")
    Integer finalAmount,

    @Schema(description = "주문 상태", example = "PENDING")
    String status,

    @Schema(description = "주문 일시", example = "2024-10-29T15:30:00")
    LocalDateTime createdAt
) {}
