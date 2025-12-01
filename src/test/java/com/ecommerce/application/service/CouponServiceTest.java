package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.entity.UserCouponStatus;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.ecommerce.domain.service.CouponDomainService;
import com.ecommerce.infrastructure.lock.DistributedLockExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 테스트")
class CouponServiceTest {

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private DistributedLockExecutor lockExecutor;

    @Mock
    private CouponDomainService couponDomainService;

    @InjectMocks
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        // lockExecutor가 supplier를 실행하도록 설정
        lenient().when(lockExecutor.executeWithLock(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    @Test
    @DisplayName("쿠폰을 발급한다")
    void issueCoupon() {
        // given
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = new UserCoupon(1L, 1L, now.plusDays(30));
        userCoupon.setId(1L);

        CouponIssueRequest request = new CouponIssueRequest(1L, 1L);

        when(couponDomainService.issueCoupon(1L, 1L)).thenReturn(userCoupon);

        // when
        UserCouponResponse response = couponService.issueCoupon(request);

        // then
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.couponId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(UserCouponStatus.AVAILABLE);
        verify(couponDomainService).issueCoupon(1L, 1L);
        verify(lockExecutor).executeWithLock(anyString(), any(Supplier.class));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    void issueCoupon_CouponNotFound() {
        // given
        CouponIssueRequest request = new CouponIssueRequest(1L, 999L);

        when(couponDomainService.issueCoupon(1L, 999L))
                .thenThrow(new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");

        verify(lockExecutor).executeWithLock(anyString(), any(Supplier.class));
    }

    @Test
    @DisplayName("사용자의 쿠폰 목록을 조회한다")
    void getUserCoupons() {
        // given
        LocalDateTime now = LocalDateTime.now();
        UserCoupon coupon1 = new UserCoupon(1L, 1L, now.plusDays(30));
        coupon1.setId(1L);
        UserCoupon coupon2 = new UserCoupon(1L, 2L, now.plusDays(30));
        coupon2.setId(2L);
        when(userCouponRepository.findByUserId(1L)).thenReturn(Arrays.asList(coupon1, coupon2));

        // when
        List<UserCouponResponse> coupons = couponService.getUserCoupons(1L);

        // then
        assertThat(coupons).hasSize(2);
        assertThat(coupons.get(0).couponId()).isEqualTo(1L);
        assertThat(coupons.get(1).couponId()).isEqualTo(2L);
    }
}
