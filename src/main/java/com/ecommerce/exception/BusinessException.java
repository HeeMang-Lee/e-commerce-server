package com.ecommerce.exception;

import com.ecommerce.dto.ResponseCode;
import lombok.Getter;

/**
 * 비즈니스 로직 예외
 *
 * 사용 예시:
 * - throw new BusinessException(ResponseCode.PRODUCT_NOT_FOUND);
 * - throw new BusinessException(ResponseCode.ORDER_INVALID_STATUS, "주문 취소는 결제 전에만 가능합니다.");
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResponseCode responseCode;
    private final String customMessage;

    public BusinessException(ResponseCode responseCode) {
        super(responseCode.getMessage());
        this.responseCode = responseCode;
        this.customMessage = null;
    }

    public BusinessException(ResponseCode responseCode, String customMessage) {
        super(customMessage);
        this.responseCode = responseCode;
        this.customMessage = customMessage;
    }

    public String getErrorMessage() {
        return customMessage != null ? customMessage : responseCode.getMessage();
    }
}
