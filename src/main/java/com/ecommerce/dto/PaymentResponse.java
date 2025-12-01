package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

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
