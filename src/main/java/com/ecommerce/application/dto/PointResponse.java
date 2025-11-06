package com.ecommerce.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PointResponse {
    private Long userId;
    private Integer balance;
}
