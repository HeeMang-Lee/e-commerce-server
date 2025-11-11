package com.ecommerce.domain.entity;

import com.ecommerce.domain.vo.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 주문 결제 Entity
 * Order와 1:1 관계로 결제 정보를 분리 관리합니다.
 */
@Entity
@Table(name = "order_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "original_amount", nullable = false, precision = 15, scale = 2)
    private Money originalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private Money discountAmount;

    @Column(name = "used_point", nullable = false, precision = 15, scale = 2)
    private Money usedPoint;

    @Column(name = "final_amount", nullable = false, precision = 15, scale = 2)
    private Money finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_data", columnDefinition = "TEXT")
    private String paymentData;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderPayment(Long orderId, Integer originalAmount,
                        Integer discountAmount, Integer usedPoint) {
        this(orderId, originalAmount, discountAmount, usedPoint, null);
    }

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

    public int getOriginalAmount() {
        return originalAmount.getAmount();
    }

    public int getDiscountAmount() {
        return discountAmount.getAmount();
    }

    public int getUsedPoint() {
        return usedPoint.getAmount();
    }

    public int getFinalAmount() {
        return finalAmount.getAmount();
    }

    public void complete() {
        if (this.paymentStatus == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        this.paymentStatus = PaymentStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    public void fail() {
        this.paymentStatus = PaymentStatus.FAILED;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setPaymentData(String paymentData) {
        this.paymentData = paymentData;
    }
}
