package com.ecommerce.domain.entity;

import com.ecommerce.domain.vo.Money;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 포인트 이력 Entity
 * 포인트 충전/사용/환불 이력을 기록합니다.
 */
@Getter
public class PointHistory {

    private Long id;
    private final Long userId;
    private final Long relatedOrderId;        // 관련 주문 ID (없을 수 있음)
    private final TransactionType transactionType;
    private final Money amount;             // 변동 금액
    private final Money balanceAfter;       // 변동 후 잔액
    private final String description;
    private final LocalDateTime createdAt;

    /**
     * 주문과 관련 없는 포인트 이력을 생성합니다 (충전 등).
     *
     * @param userId 사용자 ID
     * @param transactionType 거래 타입
     * @param amount 변동 금액
     * @param balanceAfter 변동 후 잔액
     * @param description 설명
     */
    public PointHistory(Long userId, TransactionType transactionType,
                        Integer amount, Integer balanceAfter, String description) {
        this(userId, transactionType, amount, balanceAfter, description, null);
    }

    /**
     * 주문과 관련된 포인트 이력을 생성합니다 (사용, 환불).
     *
     * @param userId 사용자 ID
     * @param transactionType 거래 타입
     * @param amount 변동 금액
     * @param balanceAfter 변동 후 잔액
     * @param description 설명
     * @param relatedOrderId 관련 주문 ID
     */
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

    /**
     * 생성자 파라미터를 검증합니다.
     */
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

    /**
     * 변동 금액을 int로 반환합니다 (하위 호환성)
     */
    public int getAmount() {
        return amount.getAmount();
    }

    /**
     * 변동 후 잔액을 int로 반환합니다 (하위 호환성)
     */
    public int getBalanceAfter() {
        return balanceAfter.getAmount();
    }

    /**
     * 이력 ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 이력 ID
     */
    public void setId(Long id) {
        this.id = id;
    }
}
