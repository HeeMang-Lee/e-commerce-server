package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class CouponPointDto {

    // 쿠폰 관련 DTO
    @Schema(description = "쿠폰 정보")
    public record CouponDto(
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

    @Schema(description = "사용자 쿠폰 정보")
    public record UserCouponDto(
        @Schema(description = "사용자 쿠폰 ID", example = "1")
        Long userCouponId,

        @Schema(description = "쿠폰 정보")
        CouponDto coupon,

        @Schema(description = "발급 일시", example = "2024-10-29T10:00:00")
        LocalDateTime issuedAt,

        @Schema(description = "만료 일시", example = "2024-11-29T23:59:59")
        LocalDateTime expiredAt,

        @Schema(description = "사용 여부", example = "false")
        boolean used
    ) {}

    @Schema(description = "사용자 쿠폰 목록 응답")
    public record UserCouponListResponse(
        @Schema(description = "사용자 쿠폰 목록")
        List<UserCouponDto> coupons,

        @Schema(description = "전체 쿠폰 수", example = "3")
        int totalCount
    ) {}

    // 포인트 관련 DTO
    @Schema(description = "포인트 잔액 응답")
    public record BalanceResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "현재 잔액", example = "50000")
        Integer balance
    ) {}

    @Schema(description = "포인트 충전 요청")
    public record ChargeRequest(
        @Schema(description = "충전 금액", example = "10000", required = true)
        Integer amount
    ) {}

    @Schema(description = "포인트 충전 응답")
    public record ChargeResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "충전 금액", example = "10000")
        Integer chargedAmount,

        @Schema(description = "현재 잔액", example = "60000")
        Integer currentBalance,

        @Schema(description = "충전 일시", example = "2024-10-29T14:00:00")
        LocalDateTime chargedAt
    ) {}

    @Schema(description = "포인트 이력")
    public record PointHistoryDto(
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

    @Schema(description = "포인트 이력 목록 응답")
    public record PointHistoryListResponse(
        @Schema(description = "포인트 이력 목록")
        List<PointHistoryDto> histories,

        @Schema(description = "전체 이력 수", example = "10")
        int totalCount
    ) {}
}
