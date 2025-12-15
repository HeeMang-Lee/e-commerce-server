package com.ecommerce.application.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 완료 이벤트
 *
 * 결제가 완료된 후 부가 로직(데이터 플랫폼 전송, 랭킹 기록)을 트리거한다.
 * 이벤트는 트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 처리된다.
 */
public record PaymentCompletedEvent(
        Long orderId,
        String orderNumber,
        Long userId,
        int originalAmount,
        int finalAmount,
        LocalDateTime paidAt,
        List<OrderItemInfo> orderItems
) {
    public record OrderItemInfo(
            Long productId,
            int quantity
    ) {}

    public String toOrderDataJson() {
        return "{\"orderId\":" + orderId +
                ",\"orderNumber\":\"" + orderNumber +
                "\",\"userId\":" + userId +
                ",\"totalAmount\":" + originalAmount +
                ",\"finalAmount\":" + finalAmount + "}";
    }
}
