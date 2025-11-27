package com.ecommerce.domain.entity;

import com.ecommerce.domain.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 Entity
 * ERD 설계에 따라 단순화된 주문 정보만 관리합니다.
 * 결제 관련 로직은 OrderPayment Entity로 분리되었습니다.
 * OrderItem은 간접 참조(ID 기반)로 관리합니다.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    public Order(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }

        this.userId = userId;
        this.orderNumber = "ORD-TEMP-" + System.nanoTime();
        initializeTimestamps();
    }

    public void assignOrderNumber() {
        if (this.getId() == null) {
            throw new IllegalStateException("주문 ID가 필요합니다");
        }
        this.orderNumber = "ORD-" + String.format("%010d", this.getId());
    }
}
