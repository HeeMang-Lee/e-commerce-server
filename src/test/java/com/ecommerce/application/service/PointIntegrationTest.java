package com.ecommerce.application.service;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.application.dto.PointHistoryResponse;
import com.ecommerce.application.dto.PointResponse;
import com.ecommerce.domain.entity.TransactionType;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.PointHistoryRepository;
import com.ecommerce.domain.repository.UserRepository;
import com.ecommerce.infrastructure.repository.InMemoryPointHistoryRepository;
import com.ecommerce.infrastructure.repository.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 포인트 통합 테스트
 * 포인트 충전, 조회, 이력 조회 기능을 통합적으로 검증합니다.
 */
@DisplayName("포인트 통합 테스트")
class PointIntegrationTest {

    private UserRepository userRepository;
    private PointHistoryRepository pointHistoryRepository;
    private PointService pointService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        pointHistoryRepository = new InMemoryPointHistoryRepository();
        pointService = new PointService(userRepository, pointHistoryRepository);
    }

    @Test
    @DisplayName("사용자가 포인트를 충전하면 잔액이 증가하고 이력이 기록된다")
    void chargePoint_Success() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 0);
        userRepository.save(user);

        PointChargeRequest request = new PointChargeRequest(1L, 10000);

        // when
        PointResponse response = pointService.chargePoint(request);

        // then
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getBalance()).isEqualTo(10000);

        // 이력 확인
        List<PointHistoryResponse> history = pointService.getPointHistory(1L);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getTransactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(history.get(0).getAmount()).isEqualTo(10000);
        assertThat(history.get(0).getBalanceAfter()).isEqualTo(10000);
    }

    @Test
    @DisplayName("여러 번 포인트를 충전하면 누적되어 증가한다")
    void chargePoint_Multiple() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 5000);
        userRepository.save(user);

        // when
        pointService.chargePoint(new PointChargeRequest(1L, 3000));
        pointService.chargePoint(new PointChargeRequest(1L, 2000));
        PointResponse response = pointService.chargePoint(new PointChargeRequest(1L, 5000));

        // then
        assertThat(response.getBalance()).isEqualTo(15000); // 5000 + 3000 + 2000 + 5000

        // 이력 확인 (3건)
        List<PointHistoryResponse> history = pointService.getPointHistory(1L);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getBalanceAfter()).isEqualTo(8000);
        assertThat(history.get(1).getBalanceAfter()).isEqualTo(10000);
        assertThat(history.get(2).getBalanceAfter()).isEqualTo(15000);
    }

    @Test
    @DisplayName("포인트 잔액을 조회할 수 있다")
    void getPoint() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 25000);
        userRepository.save(user);

        // when
        PointResponse response = pointService.getPoint(1L);

        // then
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getBalance()).isEqualTo(25000);
    }

    @Test
    @DisplayName("0원을 충전하면 예외가 발생한다")
    void chargePoint_ZeroAmount_ThrowsException() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 0);
        userRepository.save(user);

        PointChargeRequest request = new PointChargeRequest(1L, 0);

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("음수 금액을 충전하면 예외가 발생한다")
    void chargePoint_NegativeAmount_ThrowsException() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 0);
        userRepository.save(user);

        PointChargeRequest request = new PointChargeRequest(1L, -5000);

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 포인트를 충전하면 예외가 발생한다")
    void chargePoint_UserNotFound_ThrowsException() {
        // given
        PointChargeRequest request = new PointChargeRequest(999L, 10000);

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("포인트 이력이 없으면 빈 리스트가 반환된다")
    void getPointHistory_NoHistory_ReturnsEmptyList() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 0);
        userRepository.save(user);

        // when
        List<PointHistoryResponse> history = pointService.getPointHistory(1L);

        // then
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("포인트 충전과 사용 이력이 모두 조회된다")
    void getPointHistory_MixedTransactions() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 0);
        userRepository.save(user);

        // 충전
        pointService.chargePoint(new PointChargeRequest(1L, 50000));

        // 사용 (직접 차감 - 실제로는 주문 시 사용)
        user.deduct(10000);
        userRepository.save(user);
        pointHistoryRepository.save(new com.ecommerce.domain.entity.PointHistory(
                1L, TransactionType.USE, 10000, 40000, "주문 결제"
        ));

        // when
        List<PointHistoryResponse> history = pointService.getPointHistory(1L);

        // then
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getTransactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(history.get(0).getAmount()).isEqualTo(50000);
        assertThat(history.get(1).getTransactionType()).isEqualTo(TransactionType.USE);
        assertThat(history.get(1).getAmount()).isEqualTo(10000);
    }
}
