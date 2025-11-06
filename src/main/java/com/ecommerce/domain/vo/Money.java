package com.ecommerce.domain.vo;

import java.util.Objects;

/**
 * 금액을 나타내는 Value Object
 * 불변 객체로 설계되어 안전한 금액 연산을 제공합니다.
 */
public class Money {

    private final int amount;

    private Money(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다");
        }
        this.amount = amount;
    }

    /**
     * 정적 팩토리 메서드
     */
    public static Money of(int amount) {
        return new Money(amount);
    }

    /**
     * 0원을 나타내는 Money 객체
     */
    public static Money zero() {
        return new Money(0);
    }

    /**
     * 금액 값을 반환합니다
     */
    public int getAmount() {
        return amount;
    }

    /**
     * 금액을 더합니다
     */
    public Money add(Money other) {
        return new Money(this.amount + other.amount);
    }

    /**
     * 금액을 뺍니다
     */
    public Money subtract(Money other) {
        int result = this.amount - other.amount;
        if (result < 0) {
            throw new IllegalArgumentException("금액이 부족합니다");
        }
        return new Money(result);
    }

    /**
     * 금액을 곱합니다
     */
    public Money multiply(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("곱하는 값은 0 이상이어야 합니다");
        }
        return new Money(this.amount * multiplier);
    }

    /**
     * 다른 금액과 비교합니다
     */
    public boolean isGreaterThan(Money other) {
        return this.amount > other.amount;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount >= other.amount;
    }

    public boolean isLessThan(Money other) {
        return this.amount < other.amount;
    }

    public boolean isLessThanOrEqual(Money other) {
        return this.amount <= other.amount;
    }

    public boolean isZero() {
        return this.amount == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount == money.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return String.format("%d원", amount);
    }
}
