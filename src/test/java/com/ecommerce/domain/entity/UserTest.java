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
        User user = new User(1L, "홍길동", "hong@example.com", 50000);

        // then
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getEmail().getValue()).isEqualTo("hong@example.com");
        assertThat(user.getPointBalance()).isEqualTo(50000);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("포인트가 충분하면 hasPoint는 true를 반환한다")
    void hasPoint_ShouldReturnTrue_WhenPointIsSufficient() {
        // given
        User user = new User(1L, "홍길동", "hong@example.com", 50000);

        // when & then
        assertThat(user.hasPoint(30000)).isTrue();
        assertThat(user.hasPoint(50000)).isTrue();
    }

    @Test
    @DisplayName("포인트가 부족하면 hasPoint는 false를 반환한다")
    void hasPoint_ShouldReturnFalse_WhenPointIsInsufficient() {
        // given
        User user = new User(1L, "홍길동", "hong@example.com", 50000);

        // when & then
        assertThat(user.hasPoint(50001)).isFalse();
        assertThat(user.hasPoint(100000)).isFalse();
    }

    @Test
    @DisplayName("포인트를 차감한다")
    void deduct_ShouldDecreasePoint() {
        // given
        User user = new User(1L, "홍길동", "hong@example.com", 50000);

        // when
        user.deduct(20000);

        // then
        assertThat(user.getPointBalance()).isEqualTo(30000);
    }

    @Test
    @DisplayName("포인트 부족 시 차감하면 예외가 발생한다")
    void deduct_ShouldThrowException_WhenPointIsInsufficient() {
        // given
        User user = new User(1L, "홍길동", "hong@example.com", 50000);

        // when & then
        assertThatThrownBy(() -> user.deduct(60000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("포인트 부족");
    }

    @Test
    @DisplayName("포인트를 충전한다")
    void charge_ShouldIncreasePoint() {
        // given
        User user = new User(1L, "홍길동", "hong@example.com", 50000);

        // when
        user.charge(30000);

        // then
        assertThat(user.getPointBalance()).isEqualTo(80000);
    }

    @Test
    @DisplayName("0 이하 금액으로 차감 시 예외가 발생한다")
    void deduct_ShouldThrowException_WhenAmountIsInvalid() {
        // given
        User user = new User(1L, "홍길동", "hong@example.com", 50000);

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
        User user = new User(1L, "홍길동", "hong@example.com", 50000);

        // when & then
        assertThatThrownBy(() -> user.charge(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0보다 커야");

        assertThatThrownBy(() -> user.charge(-1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0보다 커야");
    }

    @Test
    @DisplayName("사용자 이름이 null이거나 빈 문자열이면 예외가 발생한다")
    void createUser_ShouldThrowException_WhenNameIsInvalid() {
        // when & then
        assertThatThrownBy(() -> new User(1L, null, "hong@example.com", 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 이름은 필수");

        assertThatThrownBy(() -> new User(1L, "", "hong@example.com", 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 이름은 필수");

        assertThatThrownBy(() -> new User(1L, "   ", "hong@example.com", 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 이름은 필수");
    }

    @Test
    @DisplayName("이메일이 null이거나 빈 문자열이면 예외가 발생한다")
    void createUser_ShouldThrowException_WhenEmailIsInvalid() {
        // when & then
        assertThatThrownBy(() -> new User(1L, "홍길동", null, 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일은 필수");

        assertThatThrownBy(() -> new User(1L, "홍길동", "", 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 이메일 형식");

        assertThatThrownBy(() -> new User(1L, "홍길동", "   ", 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 이메일 형식");
    }

    @Test
    @DisplayName("초기 포인트가 음수면 예외가 발생한다")
    void createUser_ShouldThrowException_WhenPointBalanceIsNegative() {
        // when & then
        assertThatThrownBy(() -> new User(1L, "홍길동", "hong@example.com", -1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초기 포인트는 0 이상");
    }

    @Test
    @DisplayName("여러 번 차감과 충전을 반복해도 포인트가 정확하다")
    void multipleOperations_ShouldMaintainAccuracy() {
        // given
        User user = new User(1L, "홍길동", "hong@example.com", 100000);

        // when
        user.deduct(30000);   // 70000
        user.charge(20000);   // 90000
        user.deduct(10000);   // 80000
        user.charge(5000);    // 85000

        // then
        assertThat(user.getPointBalance()).isEqualTo(85000);
    }
}
