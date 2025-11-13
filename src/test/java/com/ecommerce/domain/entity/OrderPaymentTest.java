package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderPayment Entity 테스트")
class OrderPaymentTest {

    @Test
    @DisplayName("결제를 생성한다 - 쿠폰 없음")
    void createPayment_WithoutCoupon() {
        // when
        OrderPayment payment = new OrderPayment(
                1L,           // orderId
                100000,       // originalAmount
                0,            // discountAmount
                10000         // usedPoint
        );

        // then
        assertThat(payment.getOrderId()).isEqualTo(1L);
        assertThat(payment.getOriginalAmount()).isEqualTo(100000);
        assertThat(payment.getDiscountAmount()).isEqualTo(0);
        assertThat(payment.getUsedPoint()).isEqualTo(10000);
        assertThat(payment.getFinalAmount()).isEqualTo(90000); // 100000 - 0 - 10000
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getUserCouponId()).isNull();
    }

    @Test
    @DisplayName("결제를 생성한다 - 쿠폰 사용")
    void createPayment_WithCoupon() {
        // when
        OrderPayment payment = new OrderPayment(
                1L,           // orderId
                100000,       // originalAmount
                5000,         // discountAmount
                10000,        // usedPoint
                999L          // userCouponId
        );

        // then
        assertThat(payment.getOrderId()).isEqualTo(1L);
        assertThat(payment.getOriginalAmount()).isEqualTo(100000);
        assertThat(payment.getDiscountAmount()).isEqualTo(5000);
        assertThat(payment.getUsedPoint()).isEqualTo(10000);
        assertThat(payment.getFinalAmount()).isEqualTo(85000); // 100000 - 5000 - 10000
        assertThat(payment.getUserCouponId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("결제를 완료한다")
    void complete() {
        // given
        OrderPayment payment = new OrderPayment(1L, 100000, 0, 0);

        // when
        payment.complete();

        // then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 완료된 결제는 다시 완료할 수 없다")
    void complete_ShouldThrowException_WhenAlreadyCompleted() {
        // given
        OrderPayment payment = new OrderPayment(1L, 100000, 0, 0);
        payment.complete();

        // when & then
        assertThatThrownBy(() -> payment.complete())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된 결제");
    }

    @Test
    @DisplayName("결제를 실패 처리한다")
    void fail() {
        // given
        OrderPayment payment = new OrderPayment(1L, 100000, 0, 0);

        // when
        payment.fail();

        // then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("원 금액이 음수면 예외가 발생한다")
    void createPayment_ShouldThrowException_WhenOriginalAmountIsNegative() {
        // when & then
        assertThatThrownBy(() -> new OrderPayment(1L, -1000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("원 금액은 0 이상");
    }

    @Test
    @DisplayName("할인 금액이 음수면 예외가 발생한다")
    void createPayment_ShouldThrowException_WhenDiscountAmountIsNegative() {
        // when & then
        assertThatThrownBy(() -> new OrderPayment(1L, 100000, -1000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 금액은 0 이상");
    }

    @Test
    @DisplayName("사용 포인트가 음수면 예외가 발생한다")
    void createPayment_ShouldThrowException_WhenUsedPointIsNegative() {
        // when & then
        assertThatThrownBy(() -> new OrderPayment(1L, 100000, 0, -1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용 포인트는 0 이상");
    }

    @Test
    @DisplayName("할인 금액이 원 금액을 초과하면 예외가 발생한다")
    void createPayment_ShouldThrowException_WhenDiscountExceedsOriginal() {
        // when & then
        assertThatThrownBy(() -> new OrderPayment(1L, 100000, 150000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 금액은 원 금액을 초과할 수 없습니다");
    }

    @Test
    @DisplayName("사용 포인트가 원 금액을 초과하면 예외가 발생한다")
    void createPayment_ShouldThrowException_WhenUsedPointExceedsOriginal() {
        // when & then
        assertThatThrownBy(() -> new OrderPayment(1L, 100000, 0, 150000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용 포인트는 원 금액을 초과할 수 없습니다");
    }

    @Test
    @DisplayName("할인과 포인트 합계가 원 금액을 초과하면 예외가 발생한다")
    void createPayment_ShouldThrowException_WhenTotalDiscountExceedsOriginal() {
        // when & then
        assertThatThrownBy(() -> new OrderPayment(1L, 100000, 60000, 50000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 금액과 포인트의 합이 원 금액을 초과할 수 없습니다");
    }
}
