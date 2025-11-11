package com.ecommerce.domain.entity;

import com.ecommerce.domain.vo.Email;
import com.ecommerce.domain.vo.Money;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사용자 도메인 Entity
 * 포인트 관리 비즈니스 로직을 포함합니다.
 */
@Getter
public class User {

    private Long id;
    private final String name;
    private final Email email;
    private Money pointBalance;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User(Long id, String name, String email, Integer pointBalance) {
        validateConstructorParams(id, name, pointBalance);
        this.id = id;
        this.name = name;
        this.email = Email.of(email);
        this.pointBalance = Money.of(pointBalance);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private void validateConstructorParams(Long id, String name, Integer pointBalance) {
        if (id == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다");
        }
        if (pointBalance == null || pointBalance < 0) {
            throw new IllegalArgumentException("초기 포인트는 0 이상이어야 합니다");
        }
    }

    /**
     * 포인트 잔액을 int로 반환합니다 (하위 호환성)
     */
    public int getPointBalance() {
        return pointBalance.getAmount();
    }

    /**
     * 포인트가 충분한지 확인합니다.
     *
     * @param amount 확인할 금액
     * @return 포인트가 충분하면 true
     */
    public boolean hasPoint(int amount) {
        return this.pointBalance.isGreaterThanOrEqual(Money.of(amount));
    }

    /**
     * 포인트를 차감합니다.
     *
     * @param amount 차감할 금액
     * @throws IllegalArgumentException 금액이 0 이하인 경우
     * @throws IllegalStateException 포인트가 부족한 경우
     */
    public void deduct(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다");
        }
        if (!hasPoint(amount)) {
            throw new IllegalStateException(
                    "포인트 부족: 현재 포인트 " + this.pointBalance.getAmount() + "원, 요청 금액 " + amount + "원"
            );
        }
        Money deductAmount = Money.of(amount);
        this.pointBalance = this.pointBalance.subtract(deductAmount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 포인트를 충전합니다.
     *
     * @param amount 충전할 금액
     * @throws IllegalArgumentException 금액이 0 이하인 경우
     */
    public void charge(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다");
        }
        Money chargeAmount = Money.of(amount);
        this.pointBalance = this.pointBalance.add(chargeAmount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 사용자 ID
     */
    public void setId(Long id) {
        this.id = id;
    }
}
