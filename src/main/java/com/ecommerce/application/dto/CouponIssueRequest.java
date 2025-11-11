package com.ecommerce.application.dto;

public record CouponIssueRequest(
    Long userId,
    Long couponId
) {}
