package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Coupon Entity 테스트")
class CouponTest {

    @Test
    @DisplayName("쿠폰을 생성한다")
    void createCoupon() {
        // when
        Coupon coupon = new Coupon(
                1L,
                "10% 할인 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                5000,
                LocalDate.now().plusDays(30)
        );

        // then
        assertThat(coupon.getId()).isEqualTo(1L);
        assertThat(coupon.getName()).isEqualTo("10% 할인 쿠폰");
        assertThat(coupon.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(coupon.getDiscountValue()).isEqualTo(10);
        assertThat(coupon.getMaxDiscountAmount()).isEqualTo(5000);
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부를 확인한다 - 수량 제한 없음")
    void canIssue_NoQuantityLimit() {
        // given
        Coupon coupon = new Coupon(
                1L,
                "무제한 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30)
        );

        // when & then
        assertThat(coupon.canIssue()).isTrue();
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부를 확인한다 - 수량 제한 있음")
    void canIssue_WithQuantityLimit() {
        // given
        Coupon coupon = new Coupon(
                1L,
                "한정 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30),
                100  // 총 발급 수량
        );

        // when & then - 아직 하나도 발급하지 않음
        assertThat(coupon.canIssue()).isTrue();

        // when - 50개 발급
        for (int i = 0; i < 50; i++) {
            coupon.issue();
        }

        // then - 아직 발급 가능
        assertThat(coupon.canIssue()).isTrue();
        assertThat(coupon.getRemainingQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("쿠폰 수량이 소진되면 발급할 수 없다")
    void canIssue_ShouldReturnFalse_WhenQuantityExhausted() {
        // given
        Coupon coupon = new Coupon(
                1L,
                "한정 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30),
                10
        );

        // when - 10개 모두 발급
        for (int i = 0; i < 10; i++) {
            coupon.issue();
        }

        // then
        assertThat(coupon.canIssue()).isFalse();
        assertThat(coupon.getRemainingQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("쿠폰을 발급한다")
    void issue() {
        // given
        Coupon coupon = new Coupon(
                1L,
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30),
                100
        );

        // when
        coupon.issue();

        // then
        assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
        assertThat(coupon.getRemainingQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("발급 불가능한 쿠폰은 발급 시 예외가 발생한다")
    void issue_ShouldThrowException_WhenCannotIssue() {
        // given - 수량 소진된 쿠폰
        Coupon coupon = new Coupon(
                1L,
                "소진된 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30),
                1
        );
        coupon.issue(); // 1개 발급하여 소진

        // when & then
        assertThatThrownBy(() -> coupon.issue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 가능한 수량이 없습니다");
    }

    @Test
    @DisplayName("만료 여부를 확인한다 - 만료되지 않음")
    void isExpired_ShouldReturnFalse_WhenNotExpired() {
        // given - 30일 후 만료
        Coupon coupon = new Coupon(
                1L,
                "유효한 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30)
        );

        // when & then
        assertThat(coupon.isExpired()).isFalse();
    }

    @Test
    @DisplayName("만료 여부를 확인한다 - 만료됨")
    void isExpired_ShouldReturnTrue_WhenExpired() {
        // given - 어제 만료
        Coupon coupon = new Coupon(
                1L,
                "만료된 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().minusDays(1)
        );

        // when & then
        assertThat(coupon.isExpired()).isTrue();
    }

    @Test
    @DisplayName("쿠폰 ID가 null이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenIdIsNull() {
        // when & then
        assertThatThrownBy(() -> new Coupon(
                null,
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰 ID는 필수");
    }

    @Test
    @DisplayName("쿠폰 이름이 null이거나 빈 문자열이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenNameIsInvalid() {
        // when & then
        assertThatThrownBy(() -> new Coupon(
                1L,
                null,
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰 이름은 필수");

        assertThatThrownBy(() -> new Coupon(
                1L,
                "",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰 이름은 필수");
    }

    @Test
    @DisplayName("할인 타입이 null이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenDiscountTypeIsNull() {
        // when & then
        assertThatThrownBy(() -> new Coupon(
                1L,
                "테스트 쿠폰",
                null,
                1000,
                null,
                LocalDate.now().plusDays(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 타입은 필수");
    }

    @Test
    @DisplayName("할인 값이 0 이하면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenDiscountValueIsInvalid() {
        // when & then
        assertThatThrownBy(() -> new Coupon(
                1L,
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                0,
                null,
                LocalDate.now().plusDays(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 값은 0보다 커야");

        assertThatThrownBy(() -> new Coupon(
                1L,
                "테스트 쿠폰",
                DiscountType.PERCENTAGE,
                -10,
                null,
                LocalDate.now().plusDays(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 값은 0보다 커야");
    }

    @Test
    @DisplayName("만료일이 null이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenExpiryDateIsNull() {
        // when & then
        assertThatThrownBy(() -> new Coupon(
                1L,
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료일은 필수");
    }

    @Test
    @DisplayName("총 발급 수량이 0 이하면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenTotalQuantityIsInvalid() {
        // when & then
        assertThatThrownBy(() -> new Coupon(
                1L,
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30),
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("총 발급 수량은 1개 이상");

        assertThatThrownBy(() -> new Coupon(
                1L,
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                null,
                LocalDate.now().plusDays(30),
                -10
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("총 발급 수량은 1개 이상");
    }
}
