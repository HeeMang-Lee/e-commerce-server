package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderItem Entity 테스트")
class OrderItemTest {

    @Test
    @DisplayName("상품 정보로 주문 항목을 생성한다")
    void createOrderItem_WithProduct() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        int quantity = 2;

        // when
        OrderItem orderItem = new OrderItem(product, quantity);

        // then
        assertThat(orderItem.getProductId()).isEqualTo(1L);
        assertThat(orderItem.getSnapshotProductName()).isEqualTo("무선 키보드");
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getSnapshotPrice()).isEqualTo(89000);
        assertThat(orderItem.getItemTotalAmount()).isEqualTo(178000);
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.PENDING);
        assertThat(orderItem.getCreatedAt()).isNotNull();
        assertThat(orderItem.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("주문 항목 생성 시 상품 정보를 스냅샷으로 저장한다")
    void orderItem_ShouldSnapshotProductInfo() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        int quantity = 2;

        // when
        OrderItem orderItem = new OrderItem(product, quantity);

        // 상품 가격이 변경되어도 주문 항목의 가격은 변하지 않음
        Product modifiedProduct = new Product(1L, "무선 키보드", "무선 키보드 설명", 99000, 10, "전자제품");

        // then
        assertThat(orderItem.getSnapshotPrice()).isEqualTo(89000); // 원래 가격 유지
        assertThat(orderItem.getItemTotalAmount()).isEqualTo(178000);
    }

    @Test
    @DisplayName("주문 ID를 설정할 수 있다")
    void setOrderId_ShouldUpdateOrderId() throws InterruptedException {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);
        var originalUpdatedAt = orderItem.getUpdatedAt();
        Thread.sleep(10);

        // when
        orderItem.setOrderId(100L);

        // then
        assertThat(orderItem.getOrderId()).isEqualTo(100L);
        assertThat(orderItem.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("주문 ID가 null이면 예외가 발생한다")
    void setOrderId_ShouldThrowException_WhenNull() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);

        // when & then
        assertThatThrownBy(() -> orderItem.setOrderId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문 ID는 필수");
    }

    @Test
    @DisplayName("주문 항목 상태를 변경할 수 있다")
    void updateStatus_ShouldChangeStatus() throws InterruptedException {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);
        var originalUpdatedAt = orderItem.getUpdatedAt();
        Thread.sleep(10);

        // when
        orderItem.updateStatus(OrderItemStatus.CONFIRMED);

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CONFIRMED);
        assertThat(orderItem.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("상태가 null이면 예외가 발생한다")
    void updateStatus_ShouldThrowException_WhenNull() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);

        // when & then
        assertThatThrownBy(() -> orderItem.updateStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상태는 필수");
    }

    @Test
    @DisplayName("주문 항목을 확정할 수 있다")
    void confirm_ShouldChangeStatusToConfirmed() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);

        // when
        orderItem.confirm();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CONFIRMED);
    }

    @Test
    @DisplayName("주문 항목을 취소할 수 있다")
    void cancel_ShouldChangeStatusToCancelled() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);

        // when
        orderItem.cancel();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    @DisplayName("확정된 주문 항목은 취소할 수 없다")
    void cancel_ShouldThrowException_WhenAlreadyConfirmed() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);
        orderItem.confirm();

        // when & then
        assertThatThrownBy(() -> orderItem.cancel())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 확정된 주문 항목은 취소할 수 없습니다");
    }

    @Test
    @DisplayName("확정된 주문 항목을 환불할 수 있다")
    void refund_ShouldChangeStatusToRefunded() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);
        orderItem.confirm();

        // when
        orderItem.refund();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.REFUNDED);
    }

    @Test
    @DisplayName("확정되지 않은 주문 항목은 환불할 수 없다")
    void refund_ShouldThrowException_WhenNotConfirmed() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 1);

        // when & then
        assertThatThrownBy(() -> orderItem.refund())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("확정된 주문 항목만 환불할 수 있습니다");
    }

    @Test
    @DisplayName("수량이 0 이하면 예외가 발생한다")
    void createOrderItem_ShouldThrowException_WhenQuantityIsInvalid() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");

        // when & then
        assertThatThrownBy(() -> new OrderItem(product, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 1개 이상");

        assertThatThrownBy(() -> new OrderItem(product, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 1개 이상");
    }

    @Test
    @DisplayName("상품이 null이면 예외가 발생한다")
    void createOrderItem_ShouldThrowException_WhenProductIsNull() {
        // when & then
        assertThatThrownBy(() -> new OrderItem(null, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품 정보는 필수");
    }

    @Test
    @DisplayName("항목 총액이 올바르게 계산된다")
    void calculateItemTotalAmount_ShouldBeCorrect() {
        // given
        Product product1 = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        Product product2 = new Product(2L, "무선 마우스", "무선 마우스 설명", 45000, 10, "전자제품");

        // when
        OrderItem item1 = new OrderItem(product1, 2);
        OrderItem item2 = new OrderItem(product2, 3);

        // then
        assertThat(item1.getItemTotalAmount()).isEqualTo(178000);
        assertThat(item2.getItemTotalAmount()).isEqualTo(135000);
    }

    @Test
    @DisplayName("Deprecated 메서드들은 새 필드 값을 반환한다")
    void deprecatedMethods_ShouldReturnNewFieldValues() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem orderItem = new OrderItem(product, 2);

        // when & then
        assertThat(orderItem.getProductName()).isEqualTo(orderItem.getSnapshotProductName());
        assertThat(orderItem.getPrice()).isEqualTo(orderItem.getSnapshotPrice());
        assertThat(orderItem.getSubtotal()).isEqualTo(orderItem.getItemTotalAmount());
    }
}
