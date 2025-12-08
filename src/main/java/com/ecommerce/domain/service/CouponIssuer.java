package com.ecommerce.domain.service;

public interface CouponIssuer {

    CouponIssueResult issue(Long userId, Long couponId);

    boolean isIssued(Long userId, Long couponId);
}
