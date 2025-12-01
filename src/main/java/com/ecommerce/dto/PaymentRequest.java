package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "결제 요청")
public record PaymentRequest(
    @Schema(description = "사용할 포인트", example = "10000")
    Integer usePoint
) {}
