package com.ecommerce.application.event;

public record CouponIssueEvent(
        Long couponId,
        Long userId
) {
}
