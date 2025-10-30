package com.ecommerce.controller;

import com.ecommerce.api.CouponApi;
import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.CouponPointDto;
import com.ecommerce.dto.ResponseCode;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
public class CouponController implements CouponApi {

    @Override
    public ApiResponse<CouponPointDto.UserCouponDto> issueCoupon(Long couponId, Long userId) {
        // 예시: 쿠폰이 없는 경우 예외 발생
        // throw new BusinessException(ResponseCode.COUPON_NOT_FOUND);

        // 예시: 이미 발급받은 쿠폰인 경우 예외 발생
        // throw new BusinessException(ResponseCode.COUPON_ALREADY_ISSUED);

        // 예시: 쿠폰 재고가 없는 경우 예외 발생
        // throw new BusinessException(ResponseCode.COUPON_OUT_OF_STOCK);

        CouponPointDto.CouponDto coupon = new CouponPointDto.CouponDto(
                couponId,
                "10% 할인 쿠폰",
                "PERCENTAGE",
                10,
                10000
        );

        CouponPointDto.UserCouponDto userCoupon = new CouponPointDto.UserCouponDto(
                1L,
                coupon,
                LocalDateTime.now(),
                LocalDateTime.now().plusMonths(1),
                false
        );

        return ApiResponse.of(ResponseCode.COUPON_ISSUED, userCoupon);
    }

    @Override
    public ApiResponse<CouponPointDto.UserCouponListResponse> getUserCoupons(Long userId) {
        CouponPointDto.CouponDto coupon1 = new CouponPointDto.CouponDto(
                1L,
                "10% 할인 쿠폰",
                "PERCENTAGE",
                10,
                10000
        );

        CouponPointDto.CouponDto coupon2 = new CouponPointDto.CouponDto(
                2L,
                "5000원 할인 쿠폰",
                "FIXED",
                5000,
                5000
        );

        List<CouponPointDto.UserCouponDto> coupons = Arrays.asList(
                new CouponPointDto.UserCouponDto(1L, coupon1, LocalDateTime.now().minusDays(5), LocalDateTime.now().plusMonths(1), false),
                new CouponPointDto.UserCouponDto(2L, coupon2, LocalDateTime.now().minusDays(3), LocalDateTime.now().plusMonths(1), false)
        );

        CouponPointDto.UserCouponListResponse response = new CouponPointDto.UserCouponListResponse(coupons, coupons.size());
        return ApiResponse.of(ResponseCode.COUPON_SUCCESS, response);
    }
}
