package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.application.dto.PointHistoryResponse;
import com.ecommerce.application.dto.PointResponse;
import com.ecommerce.application.service.PointService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@Validated
public class PointController {

    private final PointService pointService;

    @PostMapping("/users/{userId}/charge")
    public PointResponse chargePoint(
            @PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") Long userId,
            @RequestBody @Valid PointChargeRequest request) {
        PointChargeRequest chargeRequest = new PointChargeRequest(userId, request.amount());
        return pointService.chargePoint(chargeRequest);
    }

    @GetMapping("/users/{userId}/balance")
    public PointResponse getPoint(@PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") Long userId) {
        return pointService.getPoint(userId);
    }

    @GetMapping("/users/{userId}/history")
    public List<PointHistoryResponse> getPointHistory(@PathVariable @Positive(message = "사용자 ID는 양수여야 합니다") Long userId) {
        return pointService.getPointHistory(userId);
    }
}
