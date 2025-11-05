package com.ecommerce.application.dto;

import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PointHistoryResponse {
    private Long id;
    private TransactionType transactionType;
    private Integer amount;
    private Integer balanceAfter;
    private String description;
    private LocalDateTime createdAt;

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
