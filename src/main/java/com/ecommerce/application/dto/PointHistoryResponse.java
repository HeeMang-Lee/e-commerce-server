package com.ecommerce.application.dto;

import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.entity.TransactionType;

import java.time.LocalDateTime;

public record PointHistoryResponse(
    Long id,
    TransactionType transactionType,
    Integer amount,
    Integer balanceAfter,
    String description,
    LocalDateTime createdAt
) {

    public static PointHistoryResponse from(PointHistory history) {
        return new PointHistoryResponse(
                history.getId(),
                history.getTransactionType(),
                history.getAmount(),
                history.getBalanceAfter(),
                history.getDescription(),
                history.getCreatedAt()
        );
    }
}
