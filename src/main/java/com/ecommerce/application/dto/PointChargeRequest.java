package com.ecommerce.application.dto;

public record PointChargeRequest(
    Long userId,
    Integer amount
) {}
