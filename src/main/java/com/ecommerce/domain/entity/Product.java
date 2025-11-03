package com.ecommerce.domain.entity;

import lombok.Getter;

/**
 * 상품 도메인 Entity
 * 재고 관리 및 가격 계산 비즈니스 로직을 포함합니다.
 */
@Getter
public class Product {

    private final Long id;
    private final String name;
    private final Integer price;
    private Integer stock;
    private final String category;

    public Product(Long id, String name, Integer price, Integer stock, String category) {
        validateConstructorParams(id, name, price, stock, category);
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    private void validateConstructorParams(Long id, String name, Integer price, Integer stock, String category) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("상품 ID는 양수여야 합니다");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (price == null || price < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다");
        }
        if (stock == null || stock < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("카테고리는 필수입니다");
        }
    }

    /**
     * 재고가 충분한지 확인합니다.
     *
     * @param quantity 확인할 수량
     * @return 재고가 충분하면 true, 부족하면 false
     */
    public boolean hasStock(int quantity) {
        return this.stock >= quantity;
    }

    /**
     * 재고를 차감합니다.
     *
     * @param quantity 차감할 수량
     * @throws IllegalArgumentException 수량이 0 이하인 경우
     * @throws IllegalStateException 재고가 부족한 경우
     */
    public void reduceStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        if (!hasStock(quantity)) {
            throw new IllegalStateException(
                    String.format("재고 부족: 현재 재고 %d개, 요청 수량 %d개", this.stock, quantity)
            );
        }
        this.stock -= quantity;
    }

    /**
     * 재고를 복구합니다.
     *
     * @param quantity 복구할 수량
     * @throws IllegalArgumentException 수량이 0 이하인 경우
     */
    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        this.stock += quantity;
    }

    /**
     * 주어진 수량에 대한 총 가격을 계산합니다.
     *
     * @param quantity 수량
     * @return 총 가격
     * @throws IllegalArgumentException 수량이 1 미만인 경우
     */
    public int calculatePrice(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다");
        }
        return this.price * quantity;
    }
}
