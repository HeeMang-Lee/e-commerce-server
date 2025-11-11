package com.ecommerce.application.dto;

import java.time.LocalDateTime;

public record PaymentResponse(
    Long orderId,
    String paymentStatus,
    Integer paymentAmount,
    Integer usedPoint,
    LocalDateTime paymentAt
) {}
