package com.ecommerce.domain.entity;

import com.ecommerce.domain.vo.Money;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 주문 항목 Entity
 * 주문 당시의 상품 정보를 스냅샷으로 저장합니다.
 */
@Getter
public class OrderItem {

    private Long id;
    private Long orderId;
    private final Long productId;
    private final String snapshotProductName;
    private final Integer quantity;
    private final Money snapshotPrice;        // 주문 당시 단가 (스냅샷)
    private final Money itemTotalAmount;      // 소계 (단가 × 수량)
    private OrderItemStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
        this.snapshotProductName = product.getName();
        this.quantity = quantity;
        this.snapshotPrice = Money.of(product.getBasePrice());
        this.itemTotalAmount = this.snapshotPrice.multiply(quantity);
        this.status = OrderItemStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
     * 스냅샷 가격을 int로 반환합니다 (하위 호환성)
     */
    public int getSnapshotPrice() {
        return snapshotPrice.getAmount();
    }

    /**
     * 항목 총액을 int로 반환합니다 (하위 호환성)
     */
    public int getItemTotalAmount() {
        return itemTotalAmount.getAmount();
    }

    /**
     * 주문 항목 ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 주문 항목 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 주문 ID를 설정합니다. (Order에 추가될 때 호출)
     *
     * @param orderId 주문 ID
     * @throws IllegalArgumentException orderId가 null인 경우
     */
    public void setOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        this.orderId = orderId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 항목 상태를 변경합니다.
     *
     * @param status 변경할 상태
     * @throws IllegalArgumentException status가 null인 경우
     */
    public void updateStatus(OrderItemStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("상태는 필수입니다");
        }
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 항목을 확정합니다.
     */
    public void confirm() {
        updateStatus(OrderItemStatus.CONFIRMED);
    }

    /**
     * 주문 항목을 취소합니다.
     *
     * @throws IllegalStateException 이미 확정된 항목인 경우
     */
    public void cancel() {
        if (this.status == OrderItemStatus.CONFIRMED) {
            throw new IllegalStateException("이미 확정된 주문 항목은 취소할 수 없습니다");
        }
        updateStatus(OrderItemStatus.CANCELLED);
    }

    /**
     * 주문 항목을 환불합니다.
     *
     * @throws IllegalStateException 확정되지 않은 항목인 경우
     */
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
