package com.ecommerce.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 응답 코드 정의
 *
 * 코드 구조: {도메인}_{숫자}
 * - COMMON: 1xxx (공통)
 * - PRODUCT: 2xxx (상품)
 * - ORDER: 3xxx (주문)
 * - COUPON: 4xxx (쿠폰)
 * - POINT: 5xxx (포인트)
 */
@Getter
@RequiredArgsConstructor
public enum ResponseCode {

    // ===== 공통 (1xxx) =====
    SUCCESS(HttpStatus.OK, "COMMON_1000", "요청이 성공적으로 처리되었습니다."),
    CREATED(HttpStatus.CREATED, "COMMON_1001", "리소스가 성공적으로 생성되었습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_1400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_1401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_1403", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_1404", "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_1409", "동시성 충돌이 발생했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_1500", "서버 내부 오류가 발생했습니다."),

    // ===== 상품 (2xxx) =====
    PRODUCT_SUCCESS(HttpStatus.OK, "PRODUCT_2000", "상품 조회에 성공했습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_2001", "상품을 찾을 수 없습니다."),
    PRODUCT_OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "PRODUCT_2002", "상품 재고가 부족합니다."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "PRODUCT_2003", "판매 중지된 상품입니다."),

    // ===== 주문 (3xxx) =====
    ORDER_SUCCESS(HttpStatus.OK, "ORDER_3000", "주문 조회에 성공했습니다."),
    ORDER_CREATED(HttpStatus.CREATED, "ORDER_3001", "주문이 생성되었습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_3002", "주문을 찾을 수 없습니다."),
    ORDER_INVALID_STATUS(HttpStatus.BAD_REQUEST, "ORDER_3003", "주문 상태가 올바르지 않습니다."),
    ORDER_ALREADY_PAID(HttpStatus.BAD_REQUEST, "ORDER_3004", "이미 결제된 주문입니다."),
    ORDER_PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "ORDER_3005", "결제 처리에 실패했습니다."),
    ORDER_PAYMENT_SUCCESS(HttpStatus.OK, "ORDER_3006", "결제가 완료되었습니다."),

    // ===== 쿠폰 (4xxx) =====
    COUPON_SUCCESS(HttpStatus.OK, "COUPON_4000", "쿠폰 조회에 성공했습니다."),
    COUPON_ISSUED(HttpStatus.CREATED, "COUPON_4001", "쿠폰이 발급되었습니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_4002", "쿠폰을 찾을 수 없습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.BAD_REQUEST, "COUPON_4003", "이미 발급받은 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_4004", "만료된 쿠폰입니다."),
    COUPON_OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "COUPON_4005", "쿠폰 발급이 마감되었습니다."),
    COUPON_ALREADY_USED(HttpStatus.BAD_REQUEST, "COUPON_4006", "이미 사용된 쿠폰입니다."),

    // ===== 포인트 (5xxx) =====
    POINT_SUCCESS(HttpStatus.OK, "POINT_5000", "포인트 조회에 성공했습니다."),
    POINT_CHARGED(HttpStatus.OK, "POINT_5001", "포인트가 충전되었습니다."),
    POINT_INSUFFICIENT(HttpStatus.BAD_REQUEST, "POINT_5002", "포인트가 부족합니다."),
    POINT_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "POINT_5003", "유효하지 않은 포인트 금액입니다."),
    POINT_CHARGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "POINT_5004", "포인트 충전 한도를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    public int getStatusCode() {
        return httpStatus.value();
    }
}
