package com.ecommerce.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Product Entity 테스트")
class ProductTest {

    @Test
    @DisplayName("재고가 충분하면 hasStock은 true를 반환한다")
    void hasStock_ShouldReturnTrue_WhenStockIsSufficient() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

        // when
        boolean result = product.hasStock(5);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("재고가 부족하면 hasStock은 false를 반환한다")
    void hasStock_ShouldReturnFalse_WhenStockIsInsufficient() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

        // when
        boolean result = product.hasStock(15);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("재고 차감 시 재고가 감소한다")
    void reduceStock_ShouldDecreaseStock_WhenStockIsSufficient() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

        // when
        product.reduceStock(3);

        // then
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 부족 시 차감하면 예외가 발생한다")
    void reduceStock_ShouldThrowException_WhenStockIsInsufficient() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

        // when & then
        assertThatThrownBy(() -> product.reduceStock(15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고 부족");
    }

    @Test
    @DisplayName("재고 복구 시 재고가 증가한다")
    void restoreStock_ShouldIncreaseStock() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");
        product.reduceStock(5);

        // when
        product.restoreStock(5);

        // then
        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("가격 계산이 정확하다")
    void calculatePrice_ShouldReturnCorrectAmount() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

        // when
        int totalPrice = product.calculatePrice(3);

        // then
        assertThat(totalPrice).isEqualTo(267000);
    }

    @Test
    @DisplayName("0개 또는 음수 수량으로 가격 계산 시 예외가 발생한다")
    void calculatePrice_ShouldThrowException_WhenQuantityIsInvalid() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

        // when & then
        assertThatThrownBy(() -> product.calculatePrice(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 1개 이상");

        assertThatThrownBy(() -> product.calculatePrice(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 1개 이상");
    }

    @Test
    @DisplayName("음수 수량으로 재고 차감 시 예외가 발생한다")
    void reduceStock_ShouldThrowException_WhenQuantityIsNegative() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

        // when & then
        assertThatThrownBy(() -> product.reduceStock(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 0보다 커야");
    }

    @Test
    @DisplayName("음수 수량으로 재고 복구 시 예외가 발생한다")
    void restoreStock_ShouldThrowException_WhenQuantityIsNegative() {
        // given
        Product product = new Product(1L, "무선 키보드", 89000, 10, "전자제품");

        // when & then
        assertThatThrownBy(() -> product.restoreStock(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 0보다 커야");
    }
}
