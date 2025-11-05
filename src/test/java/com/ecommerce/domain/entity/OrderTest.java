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
        Product product1 = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        Product product2 = new Product(2L, "무선 마우스", 45000, 10, "전자제품");

        OrderItem item1 = new OrderItem(product1, 2);
        OrderItem item2 = new OrderItem(product2, 1);
        List<OrderItem> items = Arrays.asList(item1, item2);

        // when
        Order order = new Order(1L, items);

        // then
        assertThat(order.getUserId()).isEqualTo(1L);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualTo(223000); // 178000 + 45000
        assertThat(order.getDiscountAmount()).isEqualTo(0);
        assertThat(order.getFinalAmount()).isEqualTo(223000);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // TODO: Order 리팩토링 시 OrderPayment 분리와 함께 쿠폰 적용 테스트 복원
//    @Test
//    @DisplayName("쿠폰 적용 시 할인 금액이 계산된다 - 비율 할인")
//    void createOrder_WithPercentageCoupon() {
//        // given
//        Product product = new Product(1L, "무선 키보드", 100000, 10, "전자제품");
//        OrderItem item = new OrderItem(product, 1);
//        List<OrderItem> items = Arrays.asList(item);
//
//        // 10% 할인 쿠폰
//        UserCoupon coupon = new UserCoupon(1L, 1L, "10% 할인 쿠폰",
//                                           DiscountType.PERCENTAGE, 10, 50000);
//
//        // when
//        Order order = new Order(1L, items, coupon);
//
//        // then
//        assertThat(order.getTotalAmount()).isEqualTo(100000);
//        assertThat(order.getDiscountAmount()).isEqualTo(10000);  // 10%
//        assertThat(order.getFinalAmount()).isEqualTo(90000);
//    }
//
//    @Test
//    @DisplayName("쿠폰 적용 시 할인 금액이 계산된다 - 고정 금액 할인")
//    void createOrder_WithFixedAmountCoupon() {
//        // given
//        Product product = new Product(1L, "무선 키보드", 100000, 10, "전자제품");
//        OrderItem item = new OrderItem(product, 1);
//        List<OrderItem> items = Arrays.asList(item);
//
//        // 5000원 할인 쿠폰
//        UserCoupon coupon = new UserCoupon(1L, 1L, "5000원 할인 쿠폰",
//                                           DiscountType.FIXED_AMOUNT, 5000, null);
//
//        // when
//        Order order = new Order(1L, items, coupon);
//
//        // then
//        assertThat(order.getTotalAmount()).isEqualTo(100000);
//        assertThat(order.getDiscountAmount()).isEqualTo(5000);
//        assertThat(order.getFinalAmount()).isEqualTo(95000);
//    }
//
//    @Test
//    @DisplayName("비율 할인 시 최대 할인 금액을 초과할 수 없다")
//    void createOrder_WithPercentageCoupon_MaxDiscount() {
//        // given
//        Product product = new Product(1L, "모니터", 500000, 10, "전자제품");
//        OrderItem item = new OrderItem(product, 1);
//        List<OrderItem> items = Arrays.asList(item);
//
//        // 20% 할인 쿠폰 (최대 50000원)
//        UserCoupon coupon = new UserCoupon(1L, 1L, "20% 할인 쿠폰",
//                                           DiscountType.PERCENTAGE, 20, 50000);
//
//        // when
//        Order order = new Order(1L, items, coupon);
//
//        // then
//        // 20% = 100000원이지만 최대 50000원까지만 할인
//        assertThat(order.getDiscountAmount()).isEqualTo(50000);
//        assertThat(order.getFinalAmount()).isEqualTo(450000);
//    }

    @Test
    @DisplayName("주문을 완료 처리할 수 있다")
    void completeOrder() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);
        Order order = new Order(1L, Arrays.asList(item));

        // when
        order.complete();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING 상태에서만 결제 가능하다")
    void canPay_OnlyWhenPending() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);
        Order order = new Order(1L, Arrays.asList(item));

        // when & then
        assertThat(order.canPay()).isTrue();

        order.complete();
        assertThat(order.canPay()).isFalse();
    }

    @Test
    @DisplayName("이미 완료된 주문은 중복 완료할 수 없다")
    void completeOrder_ShouldThrowException_WhenAlreadyCompleted() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);
        Order order = new Order(1L, Arrays.asList(item));
        order.complete();

        // when & then
        assertThatThrownBy(() -> order.complete())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된 주문");
    }

    @Test
    @DisplayName("주문을 취소할 수 있다")
    void cancelOrder() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);
        Order order = new Order(1L, Arrays.asList(item));

        // when
        order.cancel();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("완료된 주문은 취소할 수 없다")
    void cancelOrder_ShouldThrowException_WhenCompleted() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);
        Order order = new Order(1L, Arrays.asList(item));
        order.complete();

        // when & then
        assertThatThrownBy(() -> order.cancel())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 주문은 취소할 수 없습니다");
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
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        OrderItem item = new OrderItem(product, 1);

        // when & then
        assertThatThrownBy(() -> new Order(null, Arrays.asList(item)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자 ID는 필수");
    }
}
