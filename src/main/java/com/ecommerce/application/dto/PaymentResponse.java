package com.ecommerce.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long orderId;
    private String paymentStatus;
    private Integer paymentAmount;
    private Integer usedPoint;
    private LocalDateTime paymentAt;
}
