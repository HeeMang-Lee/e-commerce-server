package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "포인트 충전 요청")
public record PointChargeRequest(
    @Schema(description = "충전 금액", example = "10000", required = true)
    Integer amount
) {}
