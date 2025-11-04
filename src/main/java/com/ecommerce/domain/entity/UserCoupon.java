package com.ecommerce.domain.entity;

import lombok.Getter;

/**
 * 사용자 쿠폰 Entity (간단 버전 - Order 테스트용)
 * 나중에 Coupon Entity와 함께 완전한 버전으로 리팩토링 예정
 */
@Getter
public class UserCoupon {

    private final Long id;
    private final Long couponId;
    private final String couponName;
    private final DiscountType discountType;
    private final Integer discountValue;
    private final Integer maxDiscountAmount;

    public UserCoupon(Long id, Long couponId, String couponName,
                      DiscountType discountType, Integer discountValue,
                      Integer maxDiscountAmount) {
        this.id = id;
        this.couponId = couponId;
        this.couponName = couponName;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
    }

    /**
     * 주문 금액에 대한 할인 금액을 계산합니다.
     *
     * @param orderAmount 주문 금액
     * @return 할인 금액
     */
    public int calculateDiscount(int orderAmount) {
        int discount;

        if (discountType == DiscountType.PERCENTAGE) {
            // 비율 할인
            discount = (int) (orderAmount * (discountValue / 100.0));

            // 최대 할인 금액 체크
            if (maxDiscountAmount != null && discount > maxDiscountAmount) {
                discount = maxDiscountAmount;
            }
        } else {
            // 고정 금액 할인
            discount = discountValue;
        }

        // 할인 금액이 주문 금액을 초과할 수 없음
        return Math.min(discount, orderAmount);
    }
}
