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
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        int quantity = 2;

        // when
        OrderItem orderItem = new OrderItem(product, quantity);

        // then
        assertThat(orderItem.getProductId()).isEqualTo(1L);
        assertThat(orderItem.getProductName()).isEqualTo("무선 키보드");
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getPrice()).isEqualTo(89000);
        assertThat(orderItem.getSubtotal()).isEqualTo(178000);
    }

    @Test
    @DisplayName("주문 항목 생성 시 상품 정보를 스냅샷으로 저장한다")
    void orderItem_ShouldSnapshotProductInfo() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        int quantity = 2;

        // when
        OrderItem orderItem = new OrderItem(product, quantity);

        // 상품 가격이 변경되어도 주문 항목의 가격은 변하지 않음
        Product modifiedProduct = new Product(1L, "무선 키보드", 99000, 10, "전자제품");

        // then
        assertThat(orderItem.getPrice()).isEqualTo(89000); // 원래 가격 유지
        assertThat(orderItem.getSubtotal()).isEqualTo(178000);
    }

    @Test
    @DisplayName("수량이 0 이하면 예외가 발생한다")
    void createOrderItem_ShouldThrowException_WhenQuantityIsInvalid() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

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
    @DisplayName("소계가 올바르게 계산된다")
    void calculateSubtotal_ShouldBeCorrect() {
        // given
        Product product1 = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        Product product2 = new Product(2L, "무선 마우스", 45000, 10, "전자제품");

        // when
        OrderItem item1 = new OrderItem(product1, 2);
        OrderItem item2 = new OrderItem(product2, 3);

        // then
        assertThat(item1.getSubtotal()).isEqualTo(178000);
        assertThat(item2.getSubtotal()).isEqualTo(135000);
    }
}
