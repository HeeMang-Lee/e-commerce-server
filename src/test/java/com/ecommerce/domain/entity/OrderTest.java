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
        assertThat(order.getOrderNumber()).startsWith("ORD-TEMP-");
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("주문 번호가 ID 기반으로 할당된다")
    void orderNumber_ShouldBeAssignedBasedOnId() {
        // given
        Order order = new Order(1L);
        order.setId(123L);

        // when
        order.assignOrderNumber();

        // then
        assertThat(order.getOrderNumber()).isEqualTo("ORD-0000000123");
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
