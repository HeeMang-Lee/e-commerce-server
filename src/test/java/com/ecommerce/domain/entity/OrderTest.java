package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Order Entity 테스트")
class OrderTest {

    @Test
    @DisplayName("주문 항목들로 주문을 생성한다")
    void createOrder_WithOrderItems() {
        // given
        Product product1 = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        Product product2 = new Product(2L, "무선 마우스", "무선 마우스 설명", 45000, 10, "전자제품");

        OrderItem item1 = new OrderItem(product1, 2);
        OrderItem item2 = new OrderItem(product2, 1);
        List<OrderItem> items = Arrays.asList(item1, item2);

        // when
        Order order = new Order(1L, items);

        // then
        assertThat(order.getUserId()).isEqualTo(1L);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getOrderNumber()).isNotNull();
        assertThat(order.getOrderNumber()).startsWith("ORD-");
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("주문 번호가 자동으로 생성된다")
    void orderNumber_ShouldBeGeneratedAutomatically() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);

        // when
        Order order1 = new Order(1L, Arrays.asList(item));
        Order order2 = new Order(1L, Arrays.asList(item));

        // then
        assertThat(order1.getOrderNumber()).isNotNull();
        assertThat(order2.getOrderNumber()).isNotNull();
        assertThat(order1.getOrderNumber()).isNotEqualTo(order2.getOrderNumber());
    }

    @Test
    @DisplayName("총 금액을 계산한다")
    void calculateTotalAmount_ShouldReturnSumOfItems() {
        // given
        Product product1 = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        Product product2 = new Product(2L, "무선 마우스", "무선 마우스 설명", 45000, 10, "전자제품");

        OrderItem item1 = new OrderItem(product1, 2);
        OrderItem item2 = new OrderItem(product2, 1);
        Order order = new Order(1L, Arrays.asList(item1, item2));

        // when
        int totalAmount = order.calculateTotalAmount();

        // then
        assertThat(totalAmount).isEqualTo(223000); // 178000 + 45000
    }

    @Test
    @DisplayName("타임스탬프를 업데이트한다")
    void updateTimestamp_ShouldUpdateUpdatedAt() throws InterruptedException {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);
        Order order = new Order(1L, Arrays.asList(item));

        var originalUpdatedAt = order.getUpdatedAt();
        Thread.sleep(10); // 시간 차이를 만들기 위해

        // when
        order.updateTimestamp();

        // then
        assertThat(order.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("주문 항목이 비어있으면 예외가 발생한다")
    void createOrder_ShouldThrowException_WhenItemsEmpty() {
        // when & then
        assertThatThrownBy(() -> new Order(1L, Arrays.asList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문 항목은 최소 1개 이상");
    }

    @Test
    @DisplayName("사용자 ID가 null이면 예외가 발생한다")
    void createOrder_ShouldThrowException_WhenUserIdIsNull() {
        // given
        Product product = new Product(1L, "무선 키보드", "무선 키보드 설명", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);

        // when & then
        assertThatThrownBy(() -> new Order(null, Arrays.asList(item)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 ID는 필수");
    }
}
