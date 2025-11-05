package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.entity.UserCouponStatus;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 테스트")
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponService couponService;

    @Test
    @DisplayName("쿠폰을 발급한다")
    void issueCoupon() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", DiscountType.PERCENTAGE, 10,
                100, now.minusDays(1), now.plusDays(30), 30);
        coupon.setId(1L);

        CouponIssueRequest request = new CouponIssueRequest(1L, 1L);
        when(couponRepository.findByIdWithLock(1L)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> {
            UserCoupon uc = invocation.getArgument(0);
            uc.setId(1L);
            return uc;
        });

        // when
        UserCouponResponse response = couponService.issueCoupon(request);

        // then
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getCouponId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        verify(couponRepository).save(coupon);
        verify(userCouponRepository).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    void issueCoupon_CouponNotFound() {
        // given
        CouponIssueRequest request = new CouponIssueRequest(1L, 999L);
        when(couponRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
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
        assertThat(coupons.get(0).getCouponId()).isEqualTo(1L);
        assertThat(coupons.get(1).getCouponId()).isEqualTo(2L);
    }
}
