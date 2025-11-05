package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PointHistory Entity 테스트")
class PointHistoryTest {

    @Test
    @DisplayName("포인트 이력을 생성한다 - 충전")
    void createPointHistory_Charge() {
        // when
        PointHistory history = new PointHistory(
                1L,                      // userId
                TransactionType.CHARGE,  // type
                50000,                   // amount
                50000,                   // balanceAfter
                "포인트 충전"
        );

        // then
        assertThat(history.getUserId()).isEqualTo(1L);
        assertThat(history.getTransactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(history.getAmount()).isEqualTo(50000);
        assertThat(history.getBalanceAfter()).isEqualTo(50000);
        assertThat(history.getDescription()).isEqualTo("포인트 충전");
        assertThat(history.getRelatedOrderId()).isNull();
        assertThat(history.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("포인트 이력을 생성한다 - 사용 (주문 ID 포함)")
    void createPointHistory_Use_WithOrderId() {
        // when
        PointHistory history = new PointHistory(
                1L,                      // userId
                TransactionType.USE,     // type
                30000,                   // amount
                20000,                   // balanceAfter
                "주문 결제",
                100L                     // relatedOrderId
        );

        // then
        assertThat(history.getUserId()).isEqualTo(1L);
        assertThat(history.getTransactionType()).isEqualTo(TransactionType.USE);
        assertThat(history.getAmount()).isEqualTo(30000);
        assertThat(history.getBalanceAfter()).isEqualTo(20000);
        assertThat(history.getDescription()).isEqualTo("주문 결제");
        assertThat(history.getRelatedOrderId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("포인트 이력을 생성한다 - 환불")
    void createPointHistory_Refund() {
        // when
        PointHistory history = new PointHistory(
                1L,                      // userId
                TransactionType.REFUND,  // type
                30000,                   // amount
                50000,                   // balanceAfter
                "주문 취소 환불",
                100L                     // relatedOrderId
        );

        // then
        assertThat(history.getTransactionType()).isEqualTo(TransactionType.REFUND);
        assertThat(history.getAmount()).isEqualTo(30000);
    }

    @Test
    @DisplayName("사용자 ID가 null이면 예외가 발생한다")
    void createPointHistory_ShouldThrowException_WhenUserIdIsNull() {
        // when & then
        assertThatThrownBy(() -> new PointHistory(
                null,
                TransactionType.CHARGE,
                50000,
                50000,
                "포인트 충전"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 ID는 필수");
    }

    @Test
    @DisplayName("거래 타입이 null이면 예외가 발생한다")
    void createPointHistory_ShouldThrowException_WhenTransactionTypeIsNull() {
        // when & then
        assertThatThrownBy(() -> new PointHistory(
                1L,
                null,
                50000,
                50000,
                "포인트 충전"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("거래 타입은 필수");
    }

    @Test
    @DisplayName("금액이 null이면 예외가 발생한다")
    void createPointHistory_ShouldThrowException_WhenAmountIsNull() {
        // when & then
        assertThatThrownBy(() -> new PointHistory(
                1L,
                TransactionType.CHARGE,
                null,
                50000,
                "포인트 충전"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 필수");
    }

    @Test
    @DisplayName("변동 후 잔액이 null이면 예외가 발생한다")
    void createPointHistory_ShouldThrowException_WhenBalanceAfterIsNull() {
        // when & then
        assertThatThrownBy(() -> new PointHistory(
                1L,
                TransactionType.CHARGE,
                50000,
                null,
                "포인트 충전"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("변동 후 잔액은 필수");
    }

    @Test
    @DisplayName("변동 후 잔액이 음수면 예외가 발생한다")
    void createPointHistory_ShouldThrowException_WhenBalanceAfterIsNegative() {
        // when & then
        assertThatThrownBy(() -> new PointHistory(
                1L,
                TransactionType.USE,
                60000,
                -10000,
                "포인트 사용"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("변동 후 잔액은 0 이상");
    }
}
