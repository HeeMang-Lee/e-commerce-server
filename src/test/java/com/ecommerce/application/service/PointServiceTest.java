package com.ecommerce.application.service;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.application.dto.PointHistoryResponse;
import com.ecommerce.application.dto.PointResponse;
import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.entity.TransactionType;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.PointHistoryRepository;
import com.ecommerce.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointService 테스트")
class PointServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private PointService pointService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(1L, "테스트", "test@test.com", 0);
    }

    @Test
    @DisplayName("포인트를 충전한다")
    void chargePoint() {
        // given
        PointChargeRequest request = new PointChargeRequest(1L, 10000);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(pointHistoryRepository.save(any(PointHistory.class))).thenReturn(null);

        // when
        PointResponse response = pointService.chargePoint(request);

        // then
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getBalance()).isEqualTo(10000);
        verify(userRepository).save(user);
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 충전할 수 없다")
    void chargePoint_UserNotFound() {
        // given
        PointChargeRequest request = new PointChargeRequest(999L, 10000);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("포인트 잔액을 조회한다")
    void getPoint() {
        // given
        user.charge(5000);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // when
        PointResponse response = pointService.getPoint(1L);

        // then
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getBalance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 포인트는 조회할 수 없다")
    void getPoint_UserNotFound() {
        // given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pointService.getPoint(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("포인트 이력을 조회한다")
    void getPointHistory() {
        // given
        PointHistory history1 = new PointHistory(1L, TransactionType.CHARGE, 10000, 10000, "충전");
        history1.setId(1L);
        PointHistory history2 = new PointHistory(1L, TransactionType.USE, 5000, 5000, "사용");
        history2.setId(2L);

        when(pointHistoryRepository.findByUserId(1L)).thenReturn(Arrays.asList(history1, history2));

        // when
        List<PointHistoryResponse> histories = pointService.getPointHistory(1L);

        // then
        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).getTransactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(0).getAmount()).isEqualTo(10000);
        assertThat(histories.get(1).getTransactionType()).isEqualTo(TransactionType.USE);
        assertThat(histories.get(1).getAmount()).isEqualTo(5000);
    }
}
