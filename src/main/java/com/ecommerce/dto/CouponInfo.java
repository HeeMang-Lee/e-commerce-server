package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "쿠폰 정보")
public record CouponInfo(
    @Schema(description = "쿠폰 ID", example = "1")
    Long couponId,

    @Schema(description = "쿠폰명", example = "10% 할인 쿠폰")
    String name,

    @Schema(description = "할인 타입", example = "PERCENTAGE")
    String discountType,

    @Schema(description = "할인 값", example = "10")
    Integer discountValue,

    @Schema(description = "최대 할인 금액", example = "10000")
    Integer maxDiscountAmount
) {}
