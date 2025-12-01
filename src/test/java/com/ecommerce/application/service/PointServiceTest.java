package com.ecommerce.application.service;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.application.dto.PointHistoryResponse;
import com.ecommerce.application.dto.PointResponse;
import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.entity.TransactionType;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.PointHistoryRepository;
import com.ecommerce.domain.repository.UserRepository;
import com.ecommerce.domain.service.PointDomainService;
import com.ecommerce.infrastructure.lock.DistributedLockExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointService 테스트")
class PointServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private DistributedLockExecutor lockExecutor;

    @Mock
    private PointDomainService pointDomainService;

    @InjectMocks
    private PointService pointService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(1L, "테스트", "test@test.com", 0);

        // lockExecutor가 supplier를 실행하도록 설정
        lenient().when(lockExecutor.executeWithLock(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    @Test
    @DisplayName("포인트를 충전한다")
    void chargePoint() {
        // given
        PointChargeRequest request = new PointChargeRequest(1L, 10000);
        when(pointDomainService.chargePoint(1L, 10000)).thenReturn(10000);

        // when
        PointResponse response = pointService.chargePoint(request);

        // then
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualTo(10000);
        verify(pointDomainService).chargePoint(1L, 10000);
        verify(lockExecutor).executeWithLock(anyString(), any(Supplier.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 충전할 수 없다")
    void chargePoint_UserNotFound() {
        // given
        PointChargeRequest request = new PointChargeRequest(999L, 10000);
        when(pointDomainService.chargePoint(999L, 10000))
                .thenThrow(new IllegalArgumentException("사용자를 찾을 수 없습니다: 999"));

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verify(lockExecutor).executeWithLock(anyString(), any(Supplier.class));
    }

    @Test
    @DisplayName("포인트 잔액을 조회한다")
    void getPoint() {
        // given
        user.charge(5000);
        when(userRepository.getByIdOrThrow(1L)).thenReturn(user);

        // when
        PointResponse response = pointService.getPoint(1L);

        // then
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 포인트는 조회할 수 없다")
    void getPoint_UserNotFound() {
        // given
        when(userRepository.getByIdOrThrow(999L)).thenThrow(new IllegalArgumentException("사용자를 찾을 수 없습니다: 999"));

        // when & then
        assertThatThrownBy(() -> pointService.getPoint(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다")
                .hasMessageContaining("999");
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
        assertThat(histories.get(0).transactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(0).amount()).isEqualTo(10000);
        assertThat(histories.get(1).transactionType()).isEqualTo(TransactionType.USE);
        assertThat(histories.get(1).amount()).isEqualTo(5000);
    }
}
