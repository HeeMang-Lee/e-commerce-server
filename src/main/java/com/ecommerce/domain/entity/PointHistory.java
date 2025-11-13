package com.ecommerce.domain.entity;

import com.ecommerce.domain.entity.base.BaseEntity;
import com.ecommerce.domain.vo.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 포인트 이력 Entity
 * 포인트 충전/사용/환불 이력을 기록합니다.
 */
@Entity
@Table(name = "point_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "related_order_id")
    private Long relatedOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private Money amount;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private Money balanceAfter;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PointHistory(Long userId, TransactionType transactionType,
                        Integer amount, Integer balanceAfter, String description) {
        this(userId, transactionType, amount, balanceAfter, description, null);
    }

    public PointHistory(Long userId, TransactionType transactionType,
                        Integer amount, Integer balanceAfter,
                        String description, Long relatedOrderId) {
        validateConstructorParams(userId, transactionType, amount, balanceAfter);

        this.userId = userId;
        this.transactionType = transactionType;
        this.amount = Money.of(amount);
        this.balanceAfter = Money.of(balanceAfter);
        this.description = description;
        this.relatedOrderId = relatedOrderId;
        this.createdAt = LocalDateTime.now();
    }

    private void validateConstructorParams(Long userId, TransactionType transactionType,
                                            Integer amount, Integer balanceAfter) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (transactionType == null) {
            throw new IllegalArgumentException("거래 타입은 필수입니다");
        }
        if (amount == null) {
            throw new IllegalArgumentException("금액은 필수입니다");
        }
        if (balanceAfter == null) {
            throw new IllegalArgumentException("변동 후 잔액은 필수입니다");
        }
        if (balanceAfter < 0) {
            throw new IllegalArgumentException("변동 후 잔액은 0 이상이어야 합니다");
        }
    }

    public int getAmount() {
        return amount.getAmount();
    }

    public int getBalanceAfter() {
        return balanceAfter.getAmount();
    }
}
