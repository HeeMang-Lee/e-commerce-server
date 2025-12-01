package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "포인트 충전 응답")
public record PointChargeResponse(
    @Schema(description = "사용자 ID", example = "1")
    Long userId,

    @Schema(description = "충전 금액", example = "10000")
    Integer chargedAmount,

    @Schema(description = "현재 잔액", example = "60000")
    Integer currentBalance,

    @Schema(description = "충전 일시", example = "2024-10-29T14:00:00")
    LocalDateTime chargedAt
) {}
