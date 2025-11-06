package com.ecommerce.domain.entity;

import com.ecommerce.domain.vo.Money;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 주문 결제 Entity
 * Order와 1:1 관계로 결제 정보를 분리 관리합니다.
 */
@Getter
public class OrderPayment {

    private Long id;
    private final Long orderId;
    private final Long userCouponId;       // 사용한 쿠폰 ID (null 가능)
    private final Money originalAmount;   // 원 금액
    private final Money discountAmount;   // 쿠폰 할인 금액
    private final Money usedPoint;        // 사용 포인트
    private final Money finalAmount;      // 최종 결제 금액
    private PaymentStatus paymentStatus;
    private String paymentData;             // 결제 관련 추가 데이터 (JSON)
    private LocalDateTime paidAt;
    private final LocalDateTime createdAt;

    /**
     * 쿠폰 없이 결제를 생성합니다.
     *
     * @param orderId 주문 ID
     * @param originalAmount 원 금액
     * @param discountAmount 할인 금액
     * @param usedPoint 사용 포인트
     */
    public OrderPayment(Long orderId, Integer originalAmount,
                        Integer discountAmount, Integer usedPoint) {
        this(orderId, originalAmount, discountAmount, usedPoint, null);
    }

    /**
     * 쿠폰을 사용하여 결제를 생성합니다.
     *
     * @param orderId 주문 ID
     * @param originalAmount 원 금액
     * @param discountAmount 할인 금액
     * @param usedPoint 사용 포인트
     * @param userCouponId 사용한 쿠폰 ID
     */
    public OrderPayment(Long orderId, Integer originalAmount,
                        Integer discountAmount, Integer usedPoint,
                        Long userCouponId) {
        validateConstructorParams(orderId, originalAmount, discountAmount, usedPoint);

        this.orderId = orderId;
        this.originalAmount = Money.of(originalAmount);
        this.discountAmount = Money.of(discountAmount);
        this.usedPoint = Money.of(usedPoint);
        this.userCouponId = userCouponId;
        this.finalAmount = this.originalAmount.subtract(this.discountAmount).subtract(this.usedPoint);
        this.paymentStatus = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 생성자 파라미터를 검증합니다.
     */
    private void validateConstructorParams(Long orderId, Integer originalAmount,
                                            Integer discountAmount, Integer usedPoint) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (originalAmount == null || originalAmount < 0) {
            throw new IllegalArgumentException("원 금액은 0 이상이어야 합니다");
        }
        if (discountAmount == null || discountAmount < 0) {
            throw new IllegalArgumentException("할인 금액은 0 이상이어야 합니다");
        }
        if (usedPoint == null || usedPoint < 0) {
            throw new IllegalArgumentException("사용 포인트는 0 이상이어야 합니다");
        }
        if (discountAmount > originalAmount) {
            throw new IllegalArgumentException("할인 금액은 원 금액을 초과할 수 없습니다");
        }
        if (usedPoint > originalAmount) {
            throw new IllegalArgumentException("사용 포인트는 원 금액을 초과할 수 없습니다");
        }
        if (discountAmount + usedPoint > originalAmount) {
            throw new IllegalArgumentException("할인 금액과 포인트의 합이 원 금액을 초과할 수 없습니다");
        }
    }

    /**
     * 원 금액을 int로 반환합니다 (하위 호환성)
     */
    public int getOriginalAmount() {
        return originalAmount.getAmount();
    }

    /**
     * 할인 금액을 int로 반환합니다 (하위 호환성)
     */
    public int getDiscountAmount() {
        return discountAmount.getAmount();
    }

    /**
     * 사용 포인트를 int로 반환합니다 (하위 호환성)
     */
    public int getUsedPoint() {
        return usedPoint.getAmount();
    }

    /**
     * 최종 결제 금액을 int로 반환합니다 (하위 호환성)
     */
    public int getFinalAmount() {
        return finalAmount.getAmount();
    }

    /**
     * 결제를 완료 처리합니다.
     *
     * @throws IllegalStateException 이미 완료된 결제인 경우
     */
    public void complete() {
        if (this.paymentStatus == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        this.paymentStatus = PaymentStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * 결제를 실패 처리합니다.
     */
    public void fail() {
        this.paymentStatus = PaymentStatus.FAILED;
    }

    /**
     * 결제 ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 결제 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 결제 관련 추가 데이터를 설정합니다.
     *
     * @param paymentData 결제 데이터 (JSON)
     */
    public void setPaymentData(String paymentData) {
        this.paymentData = paymentData;
    }
}
