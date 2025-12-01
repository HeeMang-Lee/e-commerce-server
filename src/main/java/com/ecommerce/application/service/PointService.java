package com.ecommerce.application.service;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.application.dto.PointHistoryResponse;
import com.ecommerce.application.dto.PointResponse;
import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.PointHistoryRepository;
import com.ecommerce.domain.repository.UserRepository;
import com.ecommerce.domain.service.PointDomainService;
import com.ecommerce.infrastructure.lock.DistributedLockExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 포인트 Application Facade 서비스
 *
 * 책임:
 * - 분산 락 관리 (동시성 제어)
 * - 도메인 서비스 호출 조율
 * - DTO 변환
 *
 * 주의:
 * - 비즈니스 로직은 PointDomainService에 위임
 * - Self-Invocation 없음 (분산 락 → 도메인 서비스)
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private static final String LOCK_KEY_PREFIX = "lock:point:";

    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final DistributedLockExecutor lockExecutor;
    private final PointDomainService pointDomainService;

    public PointResponse chargePoint(PointChargeRequest request) {
        String lockKey = LOCK_KEY_PREFIX + request.userId();

        return lockExecutor.executeWithLock(lockKey, () -> {
            int balance = pointDomainService.chargePoint(request.userId(), request.amount());
            return new PointResponse(request.userId(), balance);
        });
    }

    public PointResponse getPoint(Long userId) {
        User user = userRepository.getByIdOrThrow(userId);
        return new PointResponse(user.getId(), user.getPointBalance());
    }

    public PointResponse deductPoint(Long userId, int amount, String description, Long orderId) {
        String lockKey = LOCK_KEY_PREFIX + userId;

        return lockExecutor.executeWithLock(lockKey, () -> {
            int balance = pointDomainService.deductPoint(userId, amount, description, orderId);
            return new PointResponse(userId, balance);
        });
    }

    public List<PointHistoryResponse> getPointHistory(Long userId) {
        List<PointHistory> histories = pointHistoryRepository.findByUserId(userId);
        return histories.stream()
                .map(PointHistoryResponse::from)
                .toList();
    }
}
