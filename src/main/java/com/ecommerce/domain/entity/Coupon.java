package com.ecommerce.domain.entity;

import lombok.Getter;

import java.time.LocalDate;

/**
 * 쿠폰 Entity
 * 쿠폰 발급 및 수량 관리 비즈니스 로직을 포함합니다.
 */
@Getter
public class Coupon {

    private final Long id;
    private final String name;
    private final DiscountType discountType;
    private final Integer discountValue;
    private final Integer maxDiscountAmount;
    private final LocalDate expiryDate;
    private final Integer totalQuantity;  // null이면 무제한
    private Integer issuedQuantity;       // 발급된 수량

    /**
     * 수량 제한 없는 쿠폰을 생성합니다.
     *
     * @param id 쿠폰 ID
     * @param name 쿠폰 이름
     * @param discountType 할인 타입
     * @param discountValue 할인 값
     * @param maxDiscountAmount 최대 할인 금액 (null 가능)
     * @param expiryDate 만료일
     */
    public Coupon(Long id, String name, DiscountType discountType,
                  Integer discountValue, Integer maxDiscountAmount,
                  LocalDate expiryDate) {
        this(id, name, discountType, discountValue, maxDiscountAmount, expiryDate, null);
    }

    /**
     * 수량 제한이 있는 쿠폰을 생성합니다.
     *
     * @param id 쿠폰 ID
     * @param name 쿠폰 이름
     * @param discountType 할인 타입
     * @param discountValue 할인 값
     * @param maxDiscountAmount 최대 할인 금액 (null 가능)
     * @param expiryDate 만료일
     * @param totalQuantity 총 발급 수량 (null이면 무제한)
     */
    public Coupon(Long id, String name, DiscountType discountType,
                  Integer discountValue, Integer maxDiscountAmount,
                  LocalDate expiryDate, Integer totalQuantity) {
        validateConstructorParams(id, name, discountType, discountValue, expiryDate, totalQuantity);

        this.id = id;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.expiryDate = expiryDate;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
    }

    /**
     * 생성자 파라미터를 검증합니다.
     */
    private void validateConstructorParams(Long id, String name, DiscountType discountType,
                                            Integer discountValue, LocalDate expiryDate,
                                            Integer totalQuantity) {
        if (id == null) {
            throw new IllegalArgumentException("쿠폰 ID는 필수입니다");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("쿠폰 이름은 필수입니다");
        }
        if (discountType == null) {
            throw new IllegalArgumentException("할인 타입은 필수입니다");
        }
        if (discountValue == null || discountValue <= 0) {
            throw new IllegalArgumentException("할인 값은 0보다 커야 합니다");
        }
        if (expiryDate == null) {
            throw new IllegalArgumentException("만료일은 필수입니다");
        }
        if (totalQuantity != null && totalQuantity <= 0) {
            throw new IllegalArgumentException("총 발급 수량은 1개 이상이어야 합니다");
        }
    }

    /**
     * 쿠폰 발급이 가능한지 확인합니다.
     * 수량 제한이 있는 경우 남은 수량을 확인하고,
     * 수량 제한이 없는 경우 항상 true를 반환합니다.
     *
     * @return 발급 가능하면 true
     */
    public boolean canIssue() {
        if (totalQuantity == null) {
            return true;  // 무제한
        }
        return issuedQuantity < totalQuantity;
    }

    /**
     * 쿠폰을 발급합니다.
     * 발급 가능 여부를 확인한 후 발급 수량을 증가시킵니다.
     *
     * @throws IllegalStateException 발급 가능한 수량이 없는 경우
     */
    public void issue() {
        if (!canIssue()) {
            throw new IllegalStateException("발급 가능한 수량이 없습니다");
        }
        this.issuedQuantity++;
    }

    /**
     * 남은 발급 가능 수량을 반환합니다.
     * 수량 제한이 없는 경우 Integer.MAX_VALUE를 반환합니다.
     *
     * @return 남은 수량
     */
    public int getRemainingQuantity() {
        if (totalQuantity == null) {
            return Integer.MAX_VALUE;
        }
        return totalQuantity - issuedQuantity;
    }

    /**
     * 쿠폰이 만료되었는지 확인합니다.
     *
     * @return 만료되었으면 true
     */
    public boolean isExpired() {
        return LocalDate.now().isAfter(expiryDate);
    }
}
