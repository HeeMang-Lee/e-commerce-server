package com.ecommerce.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 쿠폰 정책 Entity
 * 쿠폰 발급 및 수량 관리 비즈니스 로직을 포함합니다.
 */
@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private Integer discountValue;

    @Column(name = "max_issue_count", nullable = false)
    private Integer maxIssueCount;

    @Column(name = "current_issue_count", nullable = false)
    private Integer currentIssueCount;

    @Column(name = "issue_start_date", nullable = false)
    private LocalDateTime issueStartDate;

    @Column(name = "issue_end_date", nullable = false)
    private LocalDateTime issueEndDate;

    @Column(name = "valid_period_days", nullable = false)
    private Integer validPeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public void setId(Long id) {
        this.id = id;
    }

    public boolean canIssue() {
        if (status != CouponStatus.ACTIVE) {
            return false;
        }
        if (!isWithinIssuePeriod()) {
            return false;
        }
        return currentIssueCount < maxIssueCount;
    }

    public boolean isWithinIssuePeriod() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(issueStartDate) && !now.isAfter(issueEndDate);
    }

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

    public int getRemainingQuantity() {
        return Math.max(0, maxIssueCount - currentIssueCount);
    }

    public void updateStatus(CouponStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("상태는 필수입니다");
        }
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        updateStatus(CouponStatus.ACTIVE);
    }

    public void deactivate() {
        updateStatus(CouponStatus.INACTIVE);
    }
}
