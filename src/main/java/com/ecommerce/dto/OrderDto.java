package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "주문 생성 요청")
    public static class CreateRequest {
        @Schema(description = "사용자 ID", example = "1", required = true)
        private Long userId;

        @Schema(description = "주문 상품 목록", required = true)
        private List<OrderItemRequest> items;

        @Schema(description = "사용자 쿠폰 ID", example = "1")
        private Long userCouponId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "주문 상품 요청")
    public static class OrderItemRequest {
        @Schema(description = "상품 ID", example = "1", required = true)
        private Long productId;

        @Schema(description = "수량", example = "2", required = true)
        private Integer quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "주문 응답")
    public static class Response {
        @Schema(description = "주문 ID", example = "100")
        private Long orderId;

        @Schema(description = "주문 번호", example = "ORD-20241029-001")
        private String orderNumber;

        @Schema(description = "사용자 ID", example = "1")
        private Long userId;

        @Schema(description = "주문 상품 목록")
        private List<OrderItemResponse> items;

        @Schema(description = "총 금액", example = "178000")
        private Integer totalAmount;

        @Schema(description = "할인 금액", example = "5000")
        private Integer discountAmount;

        @Schema(description = "최종 결제 금액", example = "173000")
        private Integer finalAmount;

        @Schema(description = "주문 상태", example = "PENDING")
        private String status;

        @Schema(description = "주문 일시", example = "2024-10-29T15:30:00")
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "주문 상품 응답")
    public static class OrderItemResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품명", example = "무선 키보드")
        private String productName;

        @Schema(description = "수량", example = "2")
        private Integer quantity;

        @Schema(description = "단가", example = "89000")
        private Integer price;

        @Schema(description = "소계", example = "178000")
        private Integer subtotal;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "결제 요청")
    public static class PaymentRequest {
        @Schema(description = "사용할 포인트", example = "10000")
        private Integer usePoint;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "결제 응답")
    public static class PaymentResponse {
        @Schema(description = "주문 ID", example = "100")
        private Long orderId;

        @Schema(description = "결제 상태", example = "COMPLETED")
        private String paymentStatus;

        @Schema(description = "결제 금액", example = "163000")
        private Integer paymentAmount;

        @Schema(description = "사용 포인트", example = "10000")
        private Integer usedPoint;

        @Schema(description = "결제 일시", example = "2024-10-29T15:35:00")
        private LocalDateTime paymentAt;
    }
}
