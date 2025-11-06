package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Coupon Entity 테스트")
class CouponTest {

    @Test
    @DisplayName("쿠폰을 생성한다")
    void createCoupon() {
        // given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime issueStart = now.minusDays(1);
        LocalDateTime issueEnd = now.plusDays(30);

        // when
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                100,
                issueStart,
                issueEnd,
                30
        );

        // then
        assertThat(coupon.getName()).isEqualTo("10% 할인 쿠폰");
        assertThat(coupon.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(coupon.getDiscountValue()).isEqualTo(10);
        assertThat(coupon.getMaxIssueCount()).isEqualTo(100);
        assertThat(coupon.getCurrentIssueCount()).isEqualTo(0);
        assertThat(coupon.getIssueStartDate()).isEqualTo(issueStart);
        assertThat(coupon.getIssueEndDate()).isEqualTo(issueEnd);
        assertThat(coupon.getValidPeriodDays()).isEqualTo(30);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(coupon.getCreatedAt()).isNotNull();
        assertThat(coupon.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("발급 기간 내에 있는지 확인한다")
    void isWithinIssuePeriod() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when - 발급 기간 내
        Coupon validCoupon = new Coupon(
                "유효한 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );

        // when - 발급 기간 전
        Coupon futureCoupon = new Coupon(
                "미래 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.plusDays(1),
                now.plusDays(30),
                30
        );

        // when - 발급 기간 종료
        Coupon expiredCoupon = new Coupon(
                "만료된 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(30),
                now.minusDays(1),
                30
        );

        // then
        assertThat(validCoupon.isWithinIssuePeriod()).isTrue();
        assertThat(futureCoupon.isWithinIssuePeriod()).isFalse();
        assertThat(expiredCoupon.isWithinIssuePeriod()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부를 확인한다 - 정상 케이스")
    void canIssue_ValidCase() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "정상 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );

        // when & then
        assertThat(coupon.canIssue()).isTrue();
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부를 확인한다 - 비활성 상태")
    void canIssue_InactiveStatus() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "비활성 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        coupon.deactivate();

        // when & then
        assertThat(coupon.canIssue()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부를 확인한다 - 발급 기간 아님")
    void canIssue_OutOfIssuePeriod() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "미래 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.plusDays(1),
                now.plusDays(30),
                30
        );

        // when & then
        assertThat(coupon.canIssue()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부를 확인한다 - 수량 소진")
    void canIssue_QuantityExhausted() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "소진된 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                10,
                now.minusDays(1),
                now.plusDays(30),
                30
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
    void issue() throws InterruptedException {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        var originalUpdatedAt = coupon.getUpdatedAt();
        Thread.sleep(10);

        // when
        coupon.issue();

        // then
        assertThat(coupon.getCurrentIssueCount()).isEqualTo(1);
        assertThat(coupon.getRemainingQuantity()).isEqualTo(99);
        assertThat(coupon.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("비활성 쿠폰은 발급할 수 없다")
    void issue_ShouldThrowException_WhenInactive() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "비활성 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        coupon.deactivate();

        // when & then
        assertThatThrownBy(() -> coupon.issue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 쿠폰은 발급할 수 없습니다");
    }

    @Test
    @DisplayName("발급 기간이 아니면 발급할 수 없다")
    void issue_ShouldThrowException_WhenOutOfIssuePeriod() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "미래 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.plusDays(1),
                now.plusDays(30),
                30
        );

        // when & then
        assertThatThrownBy(() -> coupon.issue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 기간이 아닙니다");
    }

    @Test
    @DisplayName("수량이 소진되면 발급할 수 없다")
    void issue_ShouldThrowException_WhenQuantityExhausted() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "소진된 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                1,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        coupon.issue(); // 1개 발급하여 소진

        // when & then
        assertThatThrownBy(() -> coupon.issue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 가능한 수량이 없습니다");
    }

    @Test
    @DisplayName("쿠폰 상태를 변경할 수 있다")
    void updateStatus() throws InterruptedException {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        var originalUpdatedAt = coupon.getUpdatedAt();
        Thread.sleep(10);

        // when
        coupon.updateStatus(CouponStatus.INACTIVE);

        // then
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.INACTIVE);
        assertThat(coupon.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("쿠폰을 활성화할 수 있다")
    void activate() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        coupon.deactivate();

        // when
        coupon.activate();

        // then
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("쿠폰을 비활성화할 수 있다")
    void deactivate() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );

        // when
        coupon.deactivate();

        // then
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.INACTIVE);
    }

    @Test
    @DisplayName("쿠폰 이름이 null이거나 빈 문자열이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenNameIsInvalid() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                null,
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰 이름은 필수");

        assertThatThrownBy(() -> new Coupon(
                "",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰 이름은 필수");
    }

    @Test
    @DisplayName("할인 타입이 null이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenDiscountTypeIsNull() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                null,
                1000,
                100,
                now,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 타입은 필수");
    }

    @Test
    @DisplayName("할인 값이 0 이하면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenDiscountValueIsInvalid() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                0,
                100,
                now,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 값은 0보다 커야");

        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.PERCENTAGE,
                -10,
                100,
                now,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 값은 0보다 커야");
    }

    @Test
    @DisplayName("비율 할인은 100% 이하여야 한다")
    void createCoupon_ShouldThrowException_WhenPercentageOver100() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.PERCENTAGE,
                101,
                100,
                now,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비율 할인은 100% 이하여야 합니다");
    }

    @Test
    @DisplayName("최대 발급 수량이 0 이하면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenMaxIssueCountIsInvalid() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                0,
                now,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 발급 수량은 1개 이상");

        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                -10,
                now,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 발급 수량은 1개 이상");
    }

    @Test
    @DisplayName("발급 시작일이 null이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenIssueStartDateIsNull() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                null,
                now.plusDays(30),
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("발급 시작일은 필수");
    }

    @Test
    @DisplayName("발급 종료일이 null이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenIssueEndDateIsNull() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now,
                null,
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("발급 종료일은 필수");
    }

    @Test
    @DisplayName("발급 종료일이 시작일보다 이전이면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenEndDateBeforeStartDate() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now.plusDays(30),
                now,
                30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("발급 종료일은 시작일보다 이후여야 합니다");
    }

    @Test
    @DisplayName("유효 기간이 0 이하면 예외가 발생한다")
    void createCoupon_ShouldThrowException_WhenValidPeriodDaysIsInvalid() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now,
                now.plusDays(30),
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효 기간은 1일 이상");

        assertThatThrownBy(() -> new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now,
                now.plusDays(30),
                -1
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효 기간은 1일 이상");
    }

    @Test
    @DisplayName("상태가 null이면 예외가 발생한다")
    void updateStatus_ShouldThrowException_WhenStatusIsNull() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                100,
                now,
                now.plusDays(30),
                30
        );

        // when & then
        assertThatThrownBy(() -> coupon.updateStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상태는 필수");
    }
}
