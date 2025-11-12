package com.ecommerce.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Order(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }

        this.userId = userId;
        this.orderNumber = generateOrderNumber();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 번호를 생성합니다.
     * 형식: ORD-{yyyyMMddHHmmss}-{나노초 끝 6자리}
     *
     * @return 생성된 주문 번호
     */
    private String generateOrderNumber() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int nanosValue = now.getNano() % 1000000;
        String nanos = ("000000" + nanosValue).substring(String.valueOf(nanosValue).length());
        return "ORD-" + timestamp + "-" + nanos;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
