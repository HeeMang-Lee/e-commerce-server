package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {

    @Schema(description = "주문 생성 요청")
    public record CreateRequest(
        @Schema(description = "사용자 ID", example = "1", required = true)
        Long userId,

        @Schema(description = "주문 상품 목록", required = true)
        List<OrderItemRequest> items,

        @Schema(description = "사용자 쿠폰 ID", example = "1")
        Long userCouponId
    ) {}

    @Schema(description = "주문 상품 요청")
    public record OrderItemRequest(
        @Schema(description = "상품 ID", example = "1", required = true)
        Long productId,

        @Schema(description = "수량", example = "2", required = true)
        Integer quantity
    ) {}

    @Schema(description = "주문 응답")
    public record Response(
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

    @Schema(description = "주문 상품 응답")
    public record OrderItemResponse(
        @Schema(description = "상품 ID", example = "1")
        Long productId,

        @Schema(description = "상품명", example = "무선 키보드")
        String productName,

        @Schema(description = "수량", example = "2")
        Integer quantity,

        @Schema(description = "단가", example = "89000")
        Integer price,

        @Schema(description = "소계", example = "178000")
        Integer subtotal
    ) {}

    @Schema(description = "결제 요청")
    public record PaymentRequest(
        @Schema(description = "사용할 포인트", example = "10000")
        Integer usePoint
    ) {}

    @Schema(description = "결제 응답")
    public record PaymentResponse(
        @Schema(description = "주문 ID", example = "100")
        Long orderId,

        @Schema(description = "결제 상태", example = "COMPLETED")
        String paymentStatus,

        @Schema(description = "결제 금액", example = "163000")
        Integer paymentAmount,

        @Schema(description = "사용 포인트", example = "10000")
        Integer usedPoint,

        @Schema(description = "결제 일시", example = "2024-10-29T15:35:00")
        LocalDateTime paymentAt
    ) {}
}
