package com.ecommerce.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record OrderRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    Long userId,

    @NotNull(message = "주문 항목은 필수입니다")
    @NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다")
    @Valid
    List<OrderItemRequest> items,

    Long userCouponId,  // optional

    @PositiveOrZero(message = "사용 포인트는 0 이상이어야 합니다")
    Integer usePoint     // optional
) {

    public record OrderItemRequest(
        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        @NotNull(message = "수량은 필수입니다")
        @Positive(message = "수량은 1개 이상이어야 합니다")
        Integer quantity
    ) {}
}
