package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.application.dto.PointHistoryResponse;
import com.ecommerce.application.dto.PointResponse;
import com.ecommerce.application.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @PostMapping("/users/{userId}/charge")
    public PointResponse chargePoint(@PathVariable Long userId, @RequestBody PointChargeRequest request) {
        PointChargeRequest chargeRequest = new PointChargeRequest(userId, request.amount());
        return pointService.chargePoint(chargeRequest);
    }

    @GetMapping("/users/{userId}/balance")
    public PointResponse getPoint(@PathVariable Long userId) {
        return pointService.getPoint(userId);
    }

    @GetMapping("/users/{userId}/history")
    public List<PointHistoryResponse> getPointHistory(@PathVariable Long userId) {
        return pointService.getPointHistory(userId);
    }
}
