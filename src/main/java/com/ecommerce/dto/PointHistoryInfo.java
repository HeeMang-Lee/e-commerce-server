package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "포인트 이력")
public record PointHistoryInfo(
    @Schema(description = "이력 ID", example = "1")
    Long historyId,

    @Schema(description = "거래 타입", example = "CHARGE")
    String transactionType,

    @Schema(description = "금액", example = "10000")
    Integer amount,

    @Schema(description = "잔액", example = "60000")
    Integer balance,

    @Schema(description = "설명", example = "포인트 충전")
    String description,

    @Schema(description = "거래 일시", example = "2024-10-29T14:00:00")
    LocalDateTime createdAt
) {}
