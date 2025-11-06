package com.ecommerce.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Money Value Object 테스트")
class MoneyTest {

    @Test
    @DisplayName("금액을 생성한다")
    void createMoney() {
        // when
        Money money = Money.of(10000);

        // then
        assertThat(money.getAmount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("음수 금액은 생성할 수 없다")
    void createMoney_NegativeAmount_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> Money.of(-1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0 이상이어야 합니다");
    }

    @Test
    @DisplayName("0원을 생성한다")
    void createZeroMoney() {
        // when
        Money money = Money.zero();

        // then
        assertThat(money.getAmount()).isEqualTo(0);
        assertThat(money.isZero()).isTrue();
    }

    @Test
    @DisplayName("금액을 더한다")
    void addMoney() {
        // given
        Money money1 = Money.of(5000);
        Money money2 = Money.of(3000);

        // when
        Money result = money1.add(money2);

        // then
        assertThat(result.getAmount()).isEqualTo(8000);
        // 불변성 확인
        assertThat(money1.getAmount()).isEqualTo(5000);
        assertThat(money2.getAmount()).isEqualTo(3000);
    }

    @Test
    @DisplayName("금액을 뺀다")
    void subtractMoney() {
        // given
        Money money1 = Money.of(10000);
        Money money2 = Money.of(3000);

        // when
        Money result = money1.subtract(money2);

        // then
        assertThat(result.getAmount()).isEqualTo(7000);
        // 불변성 확인
        assertThat(money1.getAmount()).isEqualTo(10000);
        assertThat(money2.getAmount()).isEqualTo(3000);
    }

    @Test
    @DisplayName("부족한 금액을 뺄 수 없다")
    void subtractMoney_InsufficientAmount_ThrowsException() {
        // given
        Money money1 = Money.of(5000);
        Money money2 = Money.of(10000);

        // when & then
        assertThatThrownBy(() -> money1.subtract(money2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액이 부족합니다");
    }

    @Test
    @DisplayName("금액을 곱한다")
    void multiplyMoney() {
        // given
        Money money = Money.of(5000);

        // when
        Money result = money.multiply(3);

        // then
        assertThat(result.getAmount()).isEqualTo(15000);
        // 불변성 확인
        assertThat(money.getAmount()).isEqualTo(5000);
    }

    @Test
    @DisplayName("음수를 곱할 수 없다")
    void multiplyMoney_NegativeMultiplier_ThrowsException() {
        // given
        Money money = Money.of(5000);

        // when & then
        assertThatThrownBy(() -> money.multiply(-2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("곱하는 값은 0 이상이어야 합니다");
    }

    @Test
    @DisplayName("금액을 비교한다")
    void compareMoney() {
        // given
        Money money1 = Money.of(10000);
        Money money2 = Money.of(5000);
        Money money3 = Money.of(10000);

        // then
        assertThat(money1.isGreaterThan(money2)).isTrue();
        assertThat(money2.isLessThan(money1)).isTrue();
        assertThat(money1.isGreaterThanOrEqual(money3)).isTrue();
        assertThat(money1.isLessThanOrEqual(money3)).isTrue();
    }

    @Test
    @DisplayName("같은 금액은 동등하다")
    void equalsMoney() {
        // given
        Money money1 = Money.of(10000);
        Money money2 = Money.of(10000);
        Money money3 = Money.of(5000);

        // then
        assertThat(money1).isEqualTo(money2);
        assertThat(money1).isNotEqualTo(money3);
        assertThat(money1.hashCode()).isEqualTo(money2.hashCode());
    }

    @Test
    @DisplayName("toString은 사람이 읽기 쉬운 형식을 반환한다")
    void toStringMoney() {
        // given
        Money money = Money.of(10000);

        // then
        assertThat(money.toString()).isEqualTo("10000원");
    }
}
