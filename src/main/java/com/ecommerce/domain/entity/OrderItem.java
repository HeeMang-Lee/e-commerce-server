package com.ecommerce.domain.entity;

import lombok.Getter;

/**
 * 주문 항목 Entity
 * 주문 당시의 상품 정보를 스냅샷으로 저장합니다.
 */
@Getter
public class OrderItem {

    private final Long productId;
    private final String productName;
    private final Integer quantity;
    private final Integer price;        // 주문 당시 단가 (스냅샷)
    private final Integer subtotal;     // 소계

    /**
     * 상품 정보를 기반으로 주문 항목을 생성합니다.
     *
     * @param product 상품
     * @param quantity 주문 수량
     * @throws IllegalArgumentException 상품이 null이거나 수량이 1 미만인 경우
     */
    public OrderItem(Product product, int quantity) {
        validateConstructorParams(product, quantity);

        // 주문 당시 상품 정보를 스냅샷으로 저장
        this.productId = product.getId();
        this.productName = product.getName();
        this.quantity = quantity;
        this.price = product.getPrice();
        this.subtotal = calculateSubtotal(product.getPrice(), quantity);
    }

    /**
     * 생성자 파라미터를 검증합니다.
     */
    private void validateConstructorParams(Product product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("상품 정보는 필수입니다");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다");
        }
    }

    /**
     * 소계를 계산합니다.
     *
     * @param price 단가
     * @param quantity 수량
     * @return 소계 (단가 * 수량)
     */
    private int calculateSubtotal(int price, int quantity) {
        return price * quantity;
    }
}
