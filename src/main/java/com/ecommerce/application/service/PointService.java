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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PointService {

    private static final String POINT_DESCRIPTION_CHARGE = "포인트 충전";
    private static final String LOCK_KEY_PREFIX = "ecommerce:lock:user:point:";

    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final RedissonClient redissonClient;

    /**
     * 포인트를 충전합니다.
     * Redis 분산 락을 사용하여 동시성 제어를 수행합니다.
     */
    public PointResponse chargePoint(PointChargeRequest request) {
        String lockKey = LOCK_KEY_PREFIX + request.userId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("포인트 충전 락 획득 실패: userId=" + request.userId());
            }

            return executeChargePoint(request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 포인트 충전 트랜잭션 처리 (락 획득 후 실행)
     */
    @Transactional
    private PointResponse executeChargePoint(PointChargeRequest request) {
        User user = userRepository.getByIdOrThrow(request.userId());

        user.charge(request.amount());
        userRepository.save(user);

        PointHistory history = new PointHistory(
                user.getId(),
                TransactionType.CHARGE,
                request.amount(),
                user.getPointBalance(),
                POINT_DESCRIPTION_CHARGE
        );
        pointHistoryRepository.save(history);

        return new PointResponse(user.getId(), user.getPointBalance());
    }

    public PointResponse getPoint(Long userId) {
        User user = userRepository.getByIdOrThrow(userId);
        return new PointResponse(user.getId(), user.getPointBalance());
    }

    public List<PointHistoryResponse> getPointHistory(Long userId) {
        List<PointHistory> histories = pointHistoryRepository.findByUserId(userId);
        return histories.stream()
                .map(PointHistoryResponse::from)
                .toList();
    }
}
