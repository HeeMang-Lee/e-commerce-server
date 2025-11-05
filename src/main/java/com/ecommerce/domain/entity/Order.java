package com.ecommerce.domain.entity;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 Entity
 * 주문 금액 계산 및 상태 관리 비즈니스 로직을 포함합니다.
 */
@Getter
public class Order {

    private Long id;
    private final Long userId;
    private final List<OrderItem> items;
    private final Integer totalAmount;      // 총 금액
    private final Integer discountAmount;   // 할인 금액
    private final Integer finalAmount;      // 최종 금액
    private OrderStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private Long couponId;                  // 사용된 쿠폰 ID

    /**
     * 주문을 생성합니다.
     * TODO: Order 리팩토링 시 OrderPayment 분리로 쿠폰 적용 로직 이동 예정
     *
     * @param userId 사용자 ID
     * @param items 주문 항목들
     */
    public Order(Long userId, List<OrderItem> items) {
        validateConstructorParams(userId, items);

        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.totalAmount = calculateTotalAmount(items);
        this.discountAmount = 0;  // TODO: OrderPayment로 이동
        this.finalAmount = this.totalAmount;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.couponId = null;
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
     * 주문 항목들의 총 금액을 계산합니다.
     *
     * @param items 주문 항목들
     * @return 총 금액
     */
    private int calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .mapToInt(OrderItem::getSubtotal)
                .sum();
    }

    /**
     * 결제 가능 여부를 확인합니다.
     * PENDING 상태일 때만 결제 가능합니다.
     *
     * @return 결제 가능하면 true
     */
    public boolean canPay() {
        return this.status == OrderStatus.PENDING;
    }

    /**
     * 주문을 완료 처리합니다.
     *
     * @throws IllegalStateException PENDING 상태가 아닌 경우
     */
    public void complete() {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 주문입니다");
        }
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("결제 대기 상태의 주문만 완료할 수 있습니다");
        }

        this.status = OrderStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * 주문을 취소합니다.
     *
     * @throws IllegalStateException 완료된 주문인 경우
     */
    public void cancel() {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("완료된 주문은 취소할 수 없습니다");
        }

        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 주문 ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 주문 ID
     */
    public void setId(Long id) {
        this.id = id;
    }
}
