package com.ecommerce.api;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.UserCouponInfo;
import com.ecommerce.dto.UserCouponListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Coupon", description = "쿠폰 API")
@RequestMapping("/api/coupons")
public interface CouponApi {

    @Operation(summary = "쿠폰 발급", description = "사용자에게 쿠폰을 발급합니다.")
    @PostMapping("/{couponId}/issue")
    ApiResponse<UserCouponInfo> issueCoupon(
            @Parameter(description = "쿠폰 ID", required = true, example = "1")
            @PathVariable Long couponId,
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @RequestParam Long userId
    );

    @Operation(summary = "사용자 쿠폰 목록 조회", description = "특정 사용자의 쿠폰 목록을 조회합니다.")
    @GetMapping("/users/{userId}")
    ApiResponse<UserCouponListResponse> getUserCoupons(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId
    );
}
