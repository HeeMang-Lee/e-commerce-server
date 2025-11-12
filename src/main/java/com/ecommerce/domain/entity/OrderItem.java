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
 * 주문 당시의 상품 정보를 스냅샷으로 저장하여
 * 향후 상품 정보 변경에 영향받지 않도록 합니다.
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "snapshot_product_name", nullable = false, length = 200)
    private String snapshotProductName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "snapshot_price", nullable = false, precision = 10, scale = 2)
    private Money snapshotPrice;

    @Column(name = "item_total_amount", nullable = false, precision = 15, scale = 2)
    private Money itemTotalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderItemStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public OrderItem(Product product, int quantity) {
        validateConstructorParams(product, quantity);

        this.productId = product.getId();
        this.snapshotProductName = product.getName();
        this.quantity = quantity;
        this.snapshotPrice = Money.of(product.getBasePrice());
        this.itemTotalAmount = this.snapshotPrice.multiply(quantity);
        this.status = OrderItemStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private void validateConstructorParams(Product product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("상품 정보는 필수입니다");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다");
        }
    }

    public int getSnapshotPrice() {
        return snapshotPrice.getAmount();
    }

    public int getItemTotalAmount() {
        return itemTotalAmount.getAmount();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        this.orderId = orderId;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(OrderItemStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("상태는 필수입니다");
        }
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void confirm() {
        updateStatus(OrderItemStatus.CONFIRMED);
    }

    public void cancel() {
        if (this.status == OrderItemStatus.CONFIRMED) {
            throw new IllegalStateException("이미 확정된 주문 항목은 취소할 수 없습니다");
        }
        updateStatus(OrderItemStatus.CANCELLED);
    }

    public void refund() {
        if (this.status != OrderItemStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 주문 항목만 환불할 수 있습니다");
        }
        updateStatus(OrderItemStatus.REFUNDED);
    }

    // 하위 호환성을 위한 deprecated 메서드들
    /**
     * @deprecated snapshotProductName을 사용하세요
     */
    @Deprecated
    public String getProductName() {
        return snapshotProductName;
    }

    /**
     * @deprecated getSnapshotPrice()를 사용하세요
     */
    @Deprecated
    public Integer getPrice() {
        return snapshotPrice.getAmount();
    }

    /**
     * @deprecated getItemTotalAmount()를 사용하세요
     */
    @Deprecated
    public Integer getSubtotal() {
        return itemTotalAmount.getAmount();
    }
}
