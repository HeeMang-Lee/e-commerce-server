package com.ecommerce.domain.entity;

import com.ecommerce.domain.entity.base.BaseTimeEntity;
import com.ecommerce.domain.vo.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품 도메인 Entity
 * 재고 관리 및 가격 계산 비즈니스 로직을 포함합니다.
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private Money basePrice;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "category", length = 50)
    private String category;

    public Product(Long id, String name, String description, Integer basePrice,
                   Integer stockQuantity, String category) {
        validateConstructorParams(id, name, basePrice, stockQuantity, category);
        if (id != null) {
            setId(id);
        }
        this.name = name;
        this.description = description;
        this.basePrice = Money.of(basePrice);
        this.stockQuantity = stockQuantity;
        this.status = ProductStatus.ACTIVE;
        this.category = category;
        initializeTimestamps();
    }

    private void validateConstructorParams(Long id, String name, Integer basePrice,
                                            Integer stockQuantity, String category) {
        if (id != null && id <= 0) {
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
        updateTimestamp();
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
        updateTimestamp();
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
        updateTimestamp();
    }
}
