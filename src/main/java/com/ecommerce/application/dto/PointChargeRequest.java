package com.ecommerce.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PointChargeRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    Long userId,

    @NotNull(message = "충전 금액은 필수입니다")
    @Positive(message = "충전 금액은 0보다 커야 합니다")
    Integer amount
) {}
