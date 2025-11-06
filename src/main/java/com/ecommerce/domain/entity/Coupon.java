package com.ecommerce.domain.entity;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 쿠폰 정책 Entity
 * 쿠폰 발급 및 수량 관리 비즈니스 로직을 포함합니다.
 * ERD 설계에 따라 발급 기간, 유효 기간 등을 관리합니다.
 */
@Getter
public class Coupon {

    private Long id;
    private final String name;
    private final DiscountType discountType;
    private final Integer discountValue;
    private final Integer maxIssueCount;
    private Integer currentIssueCount;
    private final LocalDateTime issueStartDate;
    private final LocalDateTime issueEndDate;
    private final Integer validPeriodDays;
    private CouponStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 쿠폰 정책을 생성합니다.
     *
     * @param name 쿠폰 이름
     * @param discountType 할인 타입
     * @param discountValue 할인 값
     * @param maxIssueCount 최대 발급 수량
     * @param issueStartDate 발급 시작일
     * @param issueEndDate 발급 종료일
     * @param validPeriodDays 유효 기간 (일)
     */
    public Coupon(String name, DiscountType discountType, Integer discountValue,
                  Integer maxIssueCount, LocalDateTime issueStartDate,
                  LocalDateTime issueEndDate, Integer validPeriodDays) {
        validateConstructorParams(name, discountType, discountValue, maxIssueCount,
                issueStartDate, issueEndDate, validPeriodDays);

        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxIssueCount = maxIssueCount;
        this.currentIssueCount = 0;
        this.issueStartDate = issueStartDate;
        this.issueEndDate = issueEndDate;
        this.validPeriodDays = validPeriodDays;
        this.status = CouponStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 생성자 파라미터를 검증합니다.
     */
    private void validateConstructorParams(String name, DiscountType discountType,
                                            Integer discountValue, Integer maxIssueCount,
                                            LocalDateTime issueStartDate, LocalDateTime issueEndDate,
                                            Integer validPeriodDays) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("쿠폰 이름은 필수입니다");
        }
        if (discountType == null) {
            throw new IllegalArgumentException("할인 타입은 필수입니다");
        }
        if (discountValue == null || discountValue <= 0) {
            throw new IllegalArgumentException("할인 값은 0보다 커야 합니다");
        }
        if (discountType == DiscountType.PERCENTAGE && discountValue > 100) {
            throw new IllegalArgumentException("비율 할인은 100% 이하여야 합니다");
        }
        if (maxIssueCount == null || maxIssueCount <= 0) {
            throw new IllegalArgumentException("최대 발급 수량은 1개 이상이어야 합니다");
        }
        if (issueStartDate == null) {
            throw new IllegalArgumentException("발급 시작일은 필수입니다");
        }
        if (issueEndDate == null) {
            throw new IllegalArgumentException("발급 종료일은 필수입니다");
        }
        if (issueEndDate.isBefore(issueStartDate)) {
            throw new IllegalArgumentException("발급 종료일은 시작일보다 이후여야 합니다");
        }
        if (validPeriodDays == null || validPeriodDays <= 0) {
            throw new IllegalArgumentException("유효 기간은 1일 이상이어야 합니다");
        }
    }

    /**
     * 쿠폰 ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 쿠폰 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 쿠폰 발급이 가능한지 확인합니다.
     * - 쿠폰이 ACTIVE 상태여야 함
     * - 현재 시간이 발급 기간 내여야 함
     * - 남은 발급 수량이 있어야 함
     *
     * @return 발급 가능하면 true
     */
    public boolean canIssue() {
        if (status != CouponStatus.ACTIVE) {
            return false;
        }
        if (!isWithinIssuePeriod()) {
            return false;
        }
        return currentIssueCount < maxIssueCount;
    }

    /**
     * 현재 시간이 발급 기간 내인지 확인합니다.
     *
     * @return 발급 기간 내이면 true
     */
    public boolean isWithinIssuePeriod() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(issueStartDate) && !now.isAfter(issueEndDate);
    }

    /**
     * 쿠폰을 발급합니다.
     * 발급 가능 여부를 확인한 후 발급 수량을 증가시킵니다.
     *
     * @throws IllegalStateException 발급 불가능한 경우
     */
    public void issue() {
        if (status != CouponStatus.ACTIVE) {
            throw new IllegalStateException("비활성화된 쿠폰은 발급할 수 없습니다");
        }
        if (!isWithinIssuePeriod()) {
            throw new IllegalStateException("발급 기간이 아닙니다");
        }
        if (currentIssueCount >= maxIssueCount) {
            throw new IllegalStateException("발급 가능한 수량이 없습니다");
        }
        this.currentIssueCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 남은 발급 가능 수량을 반환합니다.
     *
     * @return 남은 수량
     */
    public int getRemainingQuantity() {
        return Math.max(0, maxIssueCount - currentIssueCount);
    }

    /**
     * 쿠폰 상태를 변경합니다.
     *
     * @param status 변경할 상태
     * @throws IllegalArgumentException status가 null인 경우
     */
    public void updateStatus(CouponStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("상태는 필수입니다");
        }
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰을 활성화합니다.
     */
    public void activate() {
        updateStatus(CouponStatus.ACTIVE);
    }

    /**
     * 쿠폰을 비활성화합니다.
     */
    public void deactivate() {
        updateStatus(CouponStatus.INACTIVE);
    }
}
