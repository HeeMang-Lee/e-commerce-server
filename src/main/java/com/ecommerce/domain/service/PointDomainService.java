package com.ecommerce.domain.service;

import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.entity.TransactionType;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.PointHistoryRepository;
import com.ecommerce.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 도메인 서비스
 *
 * 책임:
 * - 포인트 충전/차감의 핵심 비즈니스 로직
 * - 포인트 히스토리 기록
 * - 트랜잭션 경계 관리
 *
 * 주의:
 * - 다른 도메인 서비스에 의존하지 않음 (순수 도메인 로직)
 * - 분산 락은 상위 Application Service에서 관리
 */
@Service
@RequiredArgsConstructor
public class PointDomainService {

    private static final String POINT_DESCRIPTION_CHARGE = "포인트 충전";

    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 포인트 충전
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 충전 후 잔액
     */
    @Transactional
    public int chargePoint(Long userId, int amount) {
        User user = userRepository.getByIdOrThrow(userId);
        user.charge(amount);
        userRepository.save(user);

        PointHistory history = new PointHistory(
                user.getId(),
                TransactionType.CHARGE,
                amount,
                user.getPointBalance(),
                POINT_DESCRIPTION_CHARGE
        );
        pointHistoryRepository.save(history);

        return user.getPointBalance();
    }

    /**
     * 포인트 차감
     *
     * @param userId 사용자 ID
     * @param amount 차감 금액
     * @param description 차감 사유
     * @param orderId 연관된 주문 ID (optional)
     * @return 차감 후 잔액
     */
    @Transactional
    public int deductPoint(Long userId, int amount, String description, Long orderId) {
        User user = userRepository.getByIdOrThrow(userId);
        user.deduct(amount);
        userRepository.save(user);

        PointHistory history = new PointHistory(
                user.getId(),
                TransactionType.USE,
                amount,
                user.getPointBalance(),
                description,
                orderId
        );
        pointHistoryRepository.save(history);

        return user.getPointBalance();
    }
}
