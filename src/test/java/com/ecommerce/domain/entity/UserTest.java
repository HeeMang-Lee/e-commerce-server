package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User Entity 테스트")
class UserTest {

    @Test
    @DisplayName("사용자를 생성한다")
    void createUser() {
        // when
        User user = new User(1L, "홍길동", 50000);

        // then
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getBalance()).isEqualTo(50000);
    }

    @Test
    @DisplayName("잔액이 충분하면 hasBalance는 true를 반환한다")
    void hasBalance_ShouldReturnTrue_WhenBalanceIsSufficient() {
        // given
        User user = new User(1L, "홍길동", 50000);

        // when & then
        assertThat(user.hasBalance(30000)).isTrue();
        assertThat(user.hasBalance(50000)).isTrue();
    }

    @Test
    @DisplayName("잔액이 부족하면 hasBalance는 false를 반환한다")
    void hasBalance_ShouldReturnFalse_WhenBalanceIsInsufficient() {
        // given
        User user = new User(1L, "홍길동", 50000);

        // when & then
        assertThat(user.hasBalance(50001)).isFalse();
        assertThat(user.hasBalance(100000)).isFalse();
    }

    @Test
    @DisplayName("잔액을 차감한다")
    void deduct_ShouldDecreaseBalance() {
        // given
        User user = new User(1L, "홍길동", 50000);

        // when
        user.deduct(20000);

        // then
        assertThat(user.getBalance()).isEqualTo(30000);
    }

    @Test
    @DisplayName("잔액 부족 시 차감하면 예외가 발생한다")
    void deduct_ShouldThrowException_WhenBalanceIsInsufficient() {
        // given
        User user = new User(1L, "홍길동", 50000);

        // when & then
        assertThatThrownBy(() -> user.deduct(60000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잔액 부족");
    }

    @Test
    @DisplayName("잔액을 충전한다")
    void charge_ShouldIncreaseBalance() {
        // given
        User user = new User(1L, "홍길동", 50000);

        // when
        user.charge(30000);

        // then
        assertThat(user.getBalance()).isEqualTo(80000);
    }

    @Test
    @DisplayName("0 이하 금액으로 차감 시 예외가 발생한다")
    void deduct_ShouldThrowException_WhenAmountIsInvalid() {
        // given
        User user = new User(1L, "홍길동", 50000);

        // when & then
        assertThatThrownBy(() -> user.deduct(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0보다 커야");

        assertThatThrownBy(() -> user.deduct(-1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0보다 커야");
    }

    @Test
    @DisplayName("0 이하 금액으로 충전 시 예외가 발생한다")
    void charge_ShouldThrowException_WhenAmountIsInvalid() {
        // given
        User user = new User(1L, "홍길동", 50000);

        // when & then
        assertThatThrownBy(() -> user.charge(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0보다 커야");

        assertThatThrownBy(() -> user.charge(-1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0보다 커야");
    }

    @Test
    @DisplayName("사용자 ID가 null이면 예외가 발생한다")
    void createUser_ShouldThrowException_WhenIdIsNull() {
        // when & then
        assertThatThrownBy(() -> new User(null, "홍길동", 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 ID는 필수");
    }

    @Test
    @DisplayName("사용자 이름이 null이거나 빈 문자열이면 예외가 발생한다")
    void createUser_ShouldThrowException_WhenNameIsInvalid() {
        // when & then
        assertThatThrownBy(() -> new User(1L, null, 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 이름은 필수");

        assertThatThrownBy(() -> new User(1L, "", 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 이름은 필수");

        assertThatThrownBy(() -> new User(1L, "   ", 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 이름은 필수");
    }

    @Test
    @DisplayName("초기 잔액이 음수면 예외가 발생한다")
    void createUser_ShouldThrowException_WhenBalanceIsNegative() {
        // when & then
        assertThatThrownBy(() -> new User(1L, "홍길동", -1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초기 잔액은 0 이상");
    }

    @Test
    @DisplayName("여러 번 차감과 충전을 반복해도 잔액이 정확하다")
    void multipleOperations_ShouldMaintainAccuracy() {
        // given
        User user = new User(1L, "홍길동", 100000);

        // when
        user.deduct(30000);   // 70000
        user.charge(20000);   // 90000
        user.deduct(10000);   // 80000
        user.charge(5000);    // 85000

        // then
        assertThat(user.getBalance()).isEqualTo(85000);
    }
}
