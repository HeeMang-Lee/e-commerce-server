package com.ecommerce.api;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.CouponPointDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Point", description = "포인트 API")
@RequestMapping("/api/points")
public interface PointApi {

    @Operation(summary = "포인트 잔액 조회", description = "사용자의 포인트 잔액을 조회합니다.")
    @GetMapping("/users/{userId}/balance")
    ApiResponse<CouponPointDto.BalanceResponse> getBalance(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId
    );

    @Operation(summary = "포인트 충전", description = "사용자의 포인트를 충전합니다.")
    @PostMapping("/users/{userId}/charge")
    ApiResponse<CouponPointDto.ChargeResponse> chargePoint(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "충전 요청", required = true)
            @RequestBody CouponPointDto.ChargeRequest request
    );

    @Operation(summary = "포인트 이력 조회", description = "사용자의 포인트 이력을 조회합니다.")
    @GetMapping("/users/{userId}/history")
    ApiResponse<CouponPointDto.PointHistoryListResponse> getHistory(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId
    );
}
