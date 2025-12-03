package com.ecommerce.domain.service;

/**
 * 쿠폰 발급 요청 결과
 */
public enum CouponIssueResult {

    SUCCESS("발급 요청이 접수되었습니다"),
    ALREADY_ISSUED("이미 발급받은 쿠폰입니다"),
    SOLD_OUT("쿠폰이 모두 소진되었습니다"),
    INVALID_COUPON("유효하지 않은 쿠폰입니다"),
    FAILED("발급 처리 중 오류가 발생했습니다");

    private final String message;

    CouponIssueResult(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
