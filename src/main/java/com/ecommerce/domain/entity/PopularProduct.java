package com.ecommerce.domain.entity;

import com.ecommerce.domain.vo.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 인기 상품 Entity
 * 상품별 판매 집계 정보를 관리합니다.
 */
@Entity
@Table(name = "popular_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopularProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "sales_count", nullable = false)
    private Integer salesCount;

    @Column(name = "sales_amount", nullable = false, precision = 15, scale = 2)
    private Money salesAmount;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PopularProduct(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다");
        }
        this.productId = productId;
        this.salesCount = 0;
        this.salesAmount = Money.of(0);
    }

    public int getSalesAmount() {
        return salesAmount.getAmount();
    }

    public void incrementSales(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("판매 금액은 0보다 커야 합니다");
        }
        this.salesCount++;
        this.salesAmount = this.salesAmount.add(Money.of(amount));
        this.lastUpdated = LocalDateTime.now();
    }

    public void setId(Long id) {
        this.id = id;
    }
}
