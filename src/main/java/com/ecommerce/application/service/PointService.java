package com.ecommerce.application.service;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.application.dto.PointHistoryResponse;
import com.ecommerce.application.dto.PointResponse;
import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.entity.TransactionType;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.PointHistoryRepository;
import com.ecommerce.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;

    public PointResponse chargePoint(PointChargeRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        user.charge(request.amount());
        userRepository.save(user);

        PointHistory history = new PointHistory(
                user.getId(),
                TransactionType.CHARGE,
                request.amount(),
                user.getPointBalance(),
                "포인트 충전"
        );
        pointHistoryRepository.save(history);

        return new PointResponse(user.getId(), user.getPointBalance());
    }

    public PointResponse getPoint(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        return new PointResponse(user.getId(), user.getPointBalance());
    }

    public List<PointHistoryResponse> getPointHistory(Long userId) {
        List<PointHistory> histories = pointHistoryRepository.findByUserId(userId);
        return histories.stream()
                .map(PointHistoryResponse::from)
                .collect(Collectors.toList());
    }
}
