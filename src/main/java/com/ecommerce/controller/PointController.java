package com.ecommerce.controller;

import com.ecommerce.api.PointApi;
import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.CouponPointDto;
import com.ecommerce.dto.ResponseCode;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
public class PointController implements PointApi {

    @Override
    public ApiResponse<CouponPointDto.BalanceResponse> getBalance(Long userId) {
        CouponPointDto.BalanceResponse balance = new CouponPointDto.BalanceResponse(userId, 50000);
        return ApiResponse.of(ResponseCode.POINT_SUCCESS, balance);
    }

    @Override
    public ApiResponse<CouponPointDto.ChargeResponse> chargePoint(Long userId, CouponPointDto.ChargeRequest request) {
        // 예시: 유효하지 않은 금액인 경우 예외 발생
        // if (request.getAmount() <= 0 || request.getAmount() > 1000000) {
        //     throw new BusinessException(ResponseCode.POINT_INVALID_AMOUNT);
        // }

        // 예시: 충전 한도 초과 시 예외 발생
        // throw new BusinessException(ResponseCode.POINT_CHARGE_LIMIT_EXCEEDED);

        CouponPointDto.ChargeResponse charge = new CouponPointDto.ChargeResponse(
                userId,
                request.amount(),
                50000 + request.amount(),
                LocalDateTime.now()
        );

        return ApiResponse.of(ResponseCode.POINT_CHARGED, charge);
    }

    @Override
    public ApiResponse<CouponPointDto.PointHistoryListResponse> getHistory(Long userId) {
        List<CouponPointDto.PointHistoryDto> histories = Arrays.asList(
                new CouponPointDto.PointHistoryDto(1L, "CHARGE", 10000, 60000, "포인트 충전", LocalDateTime.now().minusDays(5)),
                new CouponPointDto.PointHistoryDto(2L, "USE", -10000, 50000, "주문 결제 사용", LocalDateTime.now().minusDays(3)),
                new CouponPointDto.PointHistoryDto(3L, "CHARGE", 20000, 70000, "포인트 충전", LocalDateTime.now().minusDays(1))
        );

        CouponPointDto.PointHistoryListResponse response = new CouponPointDto.PointHistoryListResponse(histories, histories.size());
        return ApiResponse.of(ResponseCode.POINT_SUCCESS, response);
    }
}
