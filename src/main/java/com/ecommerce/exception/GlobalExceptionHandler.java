package com.ecommerce.exception;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러
 *
 * 모든 예외를 일관된 형식의 ApiResponse로 변환하여 반환합니다.
 * HTTP 상태코드와 비즈니스 코드를 함께 반환합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * BusinessException 처리
     * 비즈니스 로직에서 발생한 예외를 처리합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getResponseCode().getCode(), e.getErrorMessage());

        ApiResponse<Void> response = ApiResponse.fail(e.getResponseCode(), e.getErrorMessage());
        return ResponseEntity
                .status(e.getResponseCode().getHttpStatus())
                .body(response);
    }

    /**
     * Validation 예외 처리
     * @Valid, @Validated 어노테이션 검증 실패 시 발생합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("ValidationException: {}", errorMessage);

        ApiResponse<Void> response = ApiResponse.fail(ResponseCode.BAD_REQUEST, errorMessage);
        return ResponseEntity
                .status(ResponseCode.BAD_REQUEST.getHttpStatus())
                .body(response);
    }

    /**
     * IllegalArgumentException 처리
     * 잘못된 인자가 전달된 경우 발생합니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());

        ApiResponse<Void> response = ApiResponse.fail(ResponseCode.BAD_REQUEST, e.getMessage());
        return ResponseEntity
                .status(ResponseCode.BAD_REQUEST.getHttpStatus())
                .body(response);
    }

    /**
     * 그 외 모든 예외 처리
     * 예상하지 못한 예외가 발생한 경우입니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("UnexpectedException: ", e);

        ApiResponse<Void> response = ApiResponse.fail(ResponseCode.INTERNAL_SERVER_ERROR);
        return ResponseEntity
                .status(ResponseCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(response);
    }
}
