package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공통 API 응답")
public class ApiResponse<T> {

    @Schema(description = "성공 여부", example = "true")
    private boolean success;

    @Schema(description = "응답 코드", example = "COMMON_1000")
    private String code;

    @Schema(description = "응답 데이터")
    private T data;

    @Schema(description = "메시지", example = "요청이 성공적으로 처리되었습니다.")
    private String message;

    // ResponseCode를 사용한 성공 응답
    public static <T> ApiResponse<T> of(ResponseCode responseCode, T data) {
        return new ApiResponse<>(true, responseCode.getCode(), data, responseCode.getMessage());
    }

    // ResponseCode를 사용한 성공 응답 (메시지 커스텀)
    public static <T> ApiResponse<T> of(ResponseCode responseCode, T data, String customMessage) {
        return new ApiResponse<>(true, responseCode.getCode(), data, customMessage);
    }

    // ResponseCode를 사용한 실패 응답
    public static <T> ApiResponse<T> fail(ResponseCode responseCode) {
        return new ApiResponse<>(false, responseCode.getCode(), null, responseCode.getMessage());
    }

    // ResponseCode를 사용한 실패 응답 (메시지 커스텀)
    public static <T> ApiResponse<T> fail(ResponseCode responseCode, String customMessage) {
        return new ApiResponse<>(false, responseCode.getCode(), null, customMessage);
    }

    // 하위 호환성을 위한 기존 메서드 (Deprecated)
    @Deprecated
    public static <T> ApiResponse<T> success(T data) {
        return of(ResponseCode.SUCCESS, data);
    }

    @Deprecated
    public static <T> ApiResponse<T> success(T data, String message) {
        return of(ResponseCode.SUCCESS, data, message);
    }

    @Deprecated
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, ResponseCode.INTERNAL_SERVER_ERROR.getCode(), null, message);
    }
}
