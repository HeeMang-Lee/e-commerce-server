package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "사용자 쿠폰 정보")
public record UserCouponInfo(
    @Schema(description = "사용자 쿠폰 ID", example = "1")
    Long userCouponId,

    @Schema(description = "쿠폰 정보")
    CouponInfo coupon,

    @Schema(description = "발급 일시", example = "2024-10-29T10:00:00")
    LocalDateTime issuedAt,

    @Schema(description = "만료 일시", example = "2024-11-29T23:59:59")
    LocalDateTime expiredAt,

    @Schema(description = "사용 여부", example = "false")
    boolean used
) {}
