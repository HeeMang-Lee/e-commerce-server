package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class CouponPointDto {

    // 쿠폰 관련 DTO
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "쿠폰 정보")
    public static class CouponDto {
        @Schema(description = "쿠폰 ID", example = "1")
        private Long couponId;

        @Schema(description = "쿠폰명", example = "10% 할인 쿠폰")
        private String name;

        @Schema(description = "할인 타입", example = "PERCENTAGE")
        private String discountType;

        @Schema(description = "할인 값", example = "10")
        private Integer discountValue;

        @Schema(description = "최대 할인 금액", example = "10000")
        private Integer maxDiscountAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사용자 쿠폰 정보")
    public static class UserCouponDto {
        @Schema(description = "사용자 쿠폰 ID", example = "1")
        private Long userCouponId;

        @Schema(description = "쿠폰 정보")
        private CouponDto coupon;

        @Schema(description = "발급 일시", example = "2024-10-29T10:00:00")
        private LocalDateTime issuedAt;

        @Schema(description = "만료 일시", example = "2024-11-29T23:59:59")
        private LocalDateTime expiredAt;

        @Schema(description = "사용 여부", example = "false")
        private boolean used;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사용자 쿠폰 목록 응답")
    public static class UserCouponListResponse {
        @Schema(description = "사용자 쿠폰 목록")
        private List<UserCouponDto> coupons;

        @Schema(description = "전체 쿠폰 수", example = "3")
        private int totalCount;
    }

    // 포인트 관련 DTO
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "포인트 잔액 응답")
    public static class BalanceResponse {
        @Schema(description = "사용자 ID", example = "1")
        private Long userId;

        @Schema(description = "현재 잔액", example = "50000")
        private Integer balance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "포인트 충전 요청")
    public static class ChargeRequest {
        @Schema(description = "충전 금액", example = "10000", required = true)
        private Integer amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "포인트 충전 응답")
    public static class ChargeResponse {
        @Schema(description = "사용자 ID", example = "1")
        private Long userId;

        @Schema(description = "충전 금액", example = "10000")
        private Integer chargedAmount;

        @Schema(description = "현재 잔액", example = "60000")
        private Integer currentBalance;

        @Schema(description = "충전 일시", example = "2024-10-29T14:00:00")
        private LocalDateTime chargedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "포인트 이력")
    public static class PointHistoryDto {
        @Schema(description = "이력 ID", example = "1")
        private Long historyId;

        @Schema(description = "거래 타입", example = "CHARGE")
        private String transactionType;

        @Schema(description = "금액", example = "10000")
        private Integer amount;

        @Schema(description = "잔액", example = "60000")
        private Integer balance;

        @Schema(description = "설명", example = "포인트 충전")
        private String description;

        @Schema(description = "거래 일시", example = "2024-10-29T14:00:00")
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "포인트 이력 목록 응답")
    public static class PointHistoryListResponse {
        @Schema(description = "포인트 이력 목록")
        private List<PointHistoryDto> histories;

        @Schema(description = "전체 이력 수", example = "10")
        private int totalCount;
    }
}
