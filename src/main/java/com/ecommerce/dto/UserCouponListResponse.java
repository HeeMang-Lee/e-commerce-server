package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "사용자 쿠폰 목록 응답")
public record UserCouponListResponse(
    @Schema(description = "사용자 쿠폰 목록")
    List<UserCouponInfo> coupons,

    @Schema(description = "전체 쿠폰 수", example = "3")
    int totalCount
) {}
