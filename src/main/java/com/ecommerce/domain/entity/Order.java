package com.ecommerce.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 Entity
 * ERD 설계에 따라 단순화된 주문 정보만 관리합니다.
 * 결제 관련 로직은 OrderPayment Entity로 분리되었습니다.
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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 주문을 생성합니다.
     *
     * @param userId 사용자 ID
     * @param items 주문 항목들
     * @throws IllegalArgumentException 사용자 ID가 null이거나 항목이 비어있는 경우
     */
    public Order(Long userId, List<OrderItem> items) {
        validateConstructorParams(userId, items);

        this.userId = userId;
        this.orderNumber = generateOrderNumber();
        this.items = new ArrayList<>(items);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 생성자 파라미터를 검증합니다.
     */
    private void validateConstructorParams(Long userId, List<OrderItem> items) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 최소 1개 이상이어야 합니다");
        }
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

    /**
     * 주문 항목들의 총 금액을 계산합니다.
     * OrderPayment 생성 시 사용됩니다.
     *
     * @return 총 금액
     */
    public int calculateTotalAmount() {
        return items.stream()
                .mapToInt(OrderItem::getItemTotalAmount)
                .sum();
    }

    /**
     * 주문 ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 주문 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 주문 정보를 업데이트합니다.
     * updatedAt을 갱신합니다.
     */
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
