package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.application.service.CouponService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Validated
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/{couponId}/issue")
    public UserCouponResponse issueCoupon(
            @PathVariable @Positive(message = "쿠폰 ID는 양수여야 합니다") Long couponId,
            @RequestParam @Positive(message = "사용자 ID는 양수여야 합니다") Long userId) {
        CouponIssueRequest request = new CouponIssueRequest(userId, couponId);
        return couponService.issueCoupon(request);
    }

    @GetMapping("/users/{userId}")
    public List<UserCouponResponse> getUserCoupons(@PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") Long userId) {
        return couponService.getUserCoupons(userId);
    }
}
