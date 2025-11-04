package com.ecommerce.domain.entity;

import lombok.Getter;

/**
 * 사용자 도메인 Entity
 * 잔액 관리 비즈니스 로직을 포함합니다.
 */
@Getter
public class User {

    private final Long id;
    private final String name;
    private Integer balance;

    public User(Long id, String name, Integer balance) {
        validateConstructorParams(id, name, balance);
        this.id = id;
        this.name = name;
        this.balance = balance;
    }

    private void validateConstructorParams(Long id, String name, Integer balance) {
        if (id == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다");
        }
        if (balance == null || balance < 0) {
            throw new IllegalArgumentException("초기 잔액은 0 이상이어야 합니다");
        }
    }

    /**
     * 잔액이 충분한지 확인합니다.
     *
     * @param amount 확인할 금액
     * @return 잔액이 충분하면 true
     */
    public boolean hasBalance(int amount) {
        return this.balance >= amount;
    }

    /**
     * 잔액을 차감합니다.
     *
     * @param amount 차감할 금액
     * @throws IllegalArgumentException 금액이 0 이하인 경우
     * @throws IllegalStateException 잔액이 부족한 경우
     */
    public void deduct(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다");
        }
        if (!hasBalance(amount)) {
            throw new IllegalStateException(
                    String.format("잔액 부족: 현재 잔액 %d원, 요청 금액 %d원", this.balance, amount)
            );
        }
        this.balance -= amount;
    }

    /**
     * 잔액을 충전합니다.
     *
     * @param amount 충전할 금액
     * @throws IllegalArgumentException 금액이 0 이하인 경우
     */
    public void charge(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다");
        }
        this.balance += amount;
    }
}
