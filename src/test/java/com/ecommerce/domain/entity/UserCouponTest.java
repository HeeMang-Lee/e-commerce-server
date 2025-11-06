package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserCoupon Entity 테스트")
class UserCouponTest {

    @Test
    @DisplayName("사용자 쿠폰을 발급한다")
    void createUserCoupon() {
        // given
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

        // when
        UserCoupon userCoupon = new UserCoupon(
                1L,           // userId
                100L,         // couponId
                expiresAt
        );

        // then
        assertThat(userCoupon.getUserId()).isEqualTo(1L);
        assertThat(userCoupon.getCouponId()).isEqualTo(100L);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        assertThat(userCoupon.getIssuedAt()).isNotNull();
        assertThat(userCoupon.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(userCoupon.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("쿠폰 사용 가능 여부를 확인한다 - 사용 가능")
    void canUse_ShouldReturnTrue_WhenAvailable() {
        // given
        UserCoupon userCoupon = new UserCoupon(
                1L,
                100L,
                LocalDateTime.now().plusDays(30)
        );

        // when & then
        assertThat(userCoupon.canUse()).isTrue();
    }

    @Test
    @DisplayName("쿠폰 사용 가능 여부를 확인한다 - 이미 사용됨")
    void canUse_ShouldReturnFalse_WhenUsed() {
        // given
        UserCoupon userCoupon = new UserCoupon(
                1L,
                100L,
                LocalDateTime.now().plusDays(30)
        );
        userCoupon.use();

        // when & then
        assertThat(userCoupon.canUse()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 사용 가능 여부를 확인한다 - 만료됨")
    void canUse_ShouldReturnFalse_WhenExpired() {
        // given - 이미 만료된 쿠폰
        UserCoupon userCoupon = new UserCoupon(
                1L,
                100L,
                LocalDateTime.now().minusDays(1)
        );

        // when & then
        assertThat(userCoupon.canUse()).isFalse();
    }

    @Test
    @DisplayName("쿠폰을 사용한다")
    void use() {
        // given
        UserCoupon userCoupon = new UserCoupon(
                1L,
                100L,
                LocalDateTime.now().plusDays(30)
        );

        // when
        userCoupon.use();

        // then
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        assertThat(userCoupon.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 사용된 쿠폰은 다시 사용할 수 없다")
    void use_ShouldThrowException_WhenAlreadyUsed() {
        // given
        UserCoupon userCoupon = new UserCoupon(
                1L,
                100L,
                LocalDateTime.now().plusDays(30)
        );
        userCoupon.use();

        // when & then
        assertThatThrownBy(() -> userCoupon.use())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 사용된 쿠폰");
    }

    @Test
    @DisplayName("만료된 쿠폰은 사용할 수 없다")
    void use_ShouldThrowException_WhenExpired() {
        // given - 만료된 쿠폰
        UserCoupon userCoupon = new UserCoupon(
                1L,
                100L,
                LocalDateTime.now().minusDays(1)
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.use())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("만료된 쿠폰");
    }

    @Test
    @DisplayName("쿠폰이 만료되었는지 확인한다 - 만료되지 않음")
    void isExpired_ShouldReturnFalse_WhenNotExpired() {
        // given
        UserCoupon userCoupon = new UserCoupon(
                1L,
                100L,
                LocalDateTime.now().plusDays(30)
        );

        // when & then
        assertThat(userCoupon.isExpired()).isFalse();
    }

    @Test
    @DisplayName("쿠폰이 만료되었는지 확인한다 - 만료됨")
    void isExpired_ShouldReturnTrue_WhenExpired() {
        // given
        UserCoupon userCoupon = new UserCoupon(
                1L,
                100L,
                LocalDateTime.now().minusDays(1)
        );

        // when & then
        assertThat(userCoupon.isExpired()).isTrue();
    }

    @Test
    @DisplayName("사용자 ID가 null이면 예외가 발생한다")
    void createUserCoupon_ShouldThrowException_WhenUserIdIsNull() {
        // when & then
        assertThatThrownBy(() -> new UserCoupon(
                null,
                100L,
                LocalDateTime.now().plusDays(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 ID는 필수");
    }

    @Test
    @DisplayName("쿠폰 ID가 null이면 예외가 발생한다")
    void createUserCoupon_ShouldThrowException_WhenCouponIdIsNull() {
        // when & then
        assertThatThrownBy(() -> new UserCoupon(
                1L,
                null,
                LocalDateTime.now().plusDays(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰 ID는 필수");
    }

    @Test
    @DisplayName("만료일이 null이면 예외가 발생한다")
    void createUserCoupon_ShouldThrowException_WhenExpiresAtIsNull() {
        // when & then
        assertThatThrownBy(() -> new UserCoupon(
                1L,
                100L,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료일은 필수");
    }
}
