package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "포인트 잔액 응답")
public record PointBalanceResponse(
    @Schema(description = "사용자 ID", example = "1")
    Long userId,

    @Schema(description = "현재 잔액", example = "50000")
    Integer balance
) {}
