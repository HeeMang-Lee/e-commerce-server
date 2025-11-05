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

    @PostMapping("/charge")
    public PointResponse chargePoint(@RequestBody PointChargeRequest request) {
        return pointService.chargePoint(request);
    }

    @GetMapping("/{userId}")
    public PointResponse getPoint(@PathVariable Long userId) {
        return pointService.getPoint(userId);
    }

    @GetMapping("/{userId}/history")
    public List<PointHistoryResponse> getPointHistory(@PathVariable Long userId) {
        return pointService.getPointHistory(userId);
    }
}
