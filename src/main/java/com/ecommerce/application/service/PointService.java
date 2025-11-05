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

/**
 * 포인트 서비스
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 포인트를 충전합니다.
     *
     * @param request 충전 요청
     * @return 충전 후 포인트 정보
     */
    public PointResponse chargePoint(PointChargeRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        user.charge(request.getAmount());
        userRepository.save(user);

        // 포인트 이력 저장
        PointHistory history = new PointHistory(
                user.getId(),
                TransactionType.CHARGE,
                request.getAmount(),
                user.getPointBalance(),
                "포인트 충전"
        );
        pointHistoryRepository.save(history);

        return new PointResponse(user.getId(), user.getPointBalance());
    }

    /**
     * 사용자의 포인트 잔액을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 포인트 정보
     */
    public PointResponse getPoint(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        return new PointResponse(user.getId(), user.getPointBalance());
    }

    /**
     * 사용자의 포인트 이력을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 포인트 이력 목록
     */
    public List<PointHistoryResponse> getPointHistory(Long userId) {
        List<PointHistory> histories = pointHistoryRepository.findByUserId(userId);
        return histories.stream()
                .map(PointHistoryResponse::from)
                .collect(Collectors.toList());
    }
}
