package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.application.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/issue")
    public UserCouponResponse issueCoupon(@RequestBody CouponIssueRequest request) {
        return couponService.issueCoupon(request);
    }

    @GetMapping("/user/{userId}")
    public List<UserCouponResponse> getUserCoupons(@PathVariable Long userId) {
        return couponService.getUserCoupons(userId);
    }
}
