package com.ecommerce.infrastructure.lock;

/**
 * 분산 락 획득 실패 예외
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
