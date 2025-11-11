package com.ecommerce.domain.entity;

import com.ecommerce.domain.vo.Money;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 상품 도메인 Entity
 * 재고 관리 및 가격 계산 비즈니스 로직을 포함합니다.
 */
@Getter
public class Product {

    private Long id;
    private final String name;
    private final String description;
    private final Money basePrice;
    private Integer stockQuantity;
    private ProductStatus status;
    private final String category;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Product(Long id, String name, String description, Integer basePrice,
                   Integer stockQuantity, String category) {
        validateConstructorParams(id, name, basePrice, stockQuantity, category);
        this.id = id;
        this.name = name;
        this.description = description;
        this.basePrice = Money.of(basePrice);
        this.stockQuantity = stockQuantity;
        this.status = ProductStatus.ACTIVE;
        this.category = category;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private void validateConstructorParams(Long id, String name, Integer basePrice,
                                            Integer stockQuantity, String category) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("상품 ID는 양수여야 합니다");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (basePrice == null || basePrice < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다");
        }
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("카테고리는 필수입니다");
        }
    }

    /**
     * 기본 가격을 int로 반환합니다 (하위 호환성)
     */
    public int getBasePrice() {
        return basePrice.getAmount();
    }

    /**
     * 재고가 충분한지 확인합니다.
     *
     * @param quantity 확인할 수량
     * @return 재고가 충분하면 true, 부족하면 false
     */
    public boolean hasStock(int quantity) {
        return this.stockQuantity >= quantity;
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
                    "재고 부족: 현재 재고 " + this.stockQuantity + "개, 요청 수량 " + quantity + "개"
            );
        }
        this.stockQuantity -= quantity;
        this.updatedAt = LocalDateTime.now();
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
        this.stockQuantity += quantity;
        this.updatedAt = LocalDateTime.now();
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
        return this.basePrice.multiply(quantity).getAmount();
    }

    /**
     * 상품 상태를 변경합니다.
     *
     * @param status 변경할 상태
     */
    public void changeStatus(ProductStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("상태는 필수입니다");
        }
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 상품 ID
     */
    public void setId(Long id) {
        this.id = id;
    }
}
