package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Order Entity 테스트")
class OrderTest {

    @Test
    @DisplayName("사용자 ID로 주문을 생성한다")
    void createOrder_WithUserId() {
        // when
        Order order = new Order(1L);

        // then
        assertThat(order.getUserId()).isEqualTo(1L);
        assertThat(order.getOrderNumber()).isNotNull();
        assertThat(order.getOrderNumber()).startsWith("ORD-");
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("주문 번호가 자동으로 생성된다")
    void orderNumber_ShouldBeGeneratedAutomatically() {
        // when
        Order order1 = new Order(1L);
        Order order2 = new Order(1L);

        // then
        assertThat(order1.getOrderNumber()).isNotNull();
        assertThat(order2.getOrderNumber()).isNotNull();
        assertThat(order1.getOrderNumber()).isNotEqualTo(order2.getOrderNumber());
    }

    @Test
    @DisplayName("타임스탬프를 업데이트한다")
    void updateTimestamp_ShouldUpdateUpdatedAt() throws InterruptedException {
        // given
        Order order = new Order(1L);

        var originalUpdatedAt = order.getUpdatedAt();
        Thread.sleep(10); // 시간 차이를 만들기 위해

        // when
        order.updateTimestamp();

        // then
        assertThat(order.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

}
