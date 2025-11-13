package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.infrastructure.persistence.repository.JpaCouponRepository;
import com.ecommerce.infrastructure.persistence.repository.JpaUserCouponRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 쿠폰 통합 테스트
 * 쿠폰 발급, 조회, 만료 등 기본 기능을 통합적으로 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("쿠폰 통합 테스트")
class CouponIntegrationTest {

    @Autowired
    private JpaCouponRepository couponRepository;

    @Autowired
    private JpaUserCouponRepository userCouponRepository;

    @Autowired
    private CouponService couponService;

    @AfterEach
    void tearDown() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자가 쿠폰을 발급받을 수 있다")
    void issueCoupon_Success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "10% 할인 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        CouponIssueRequest request = new CouponIssueRequest(1L, savedCoupon.getId());

        // when
        UserCouponResponse response = couponService.issueCoupon(request);

        // then
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.couponId()).isEqualTo(savedCoupon.getId());
        assertThat(response.status()).isEqualTo(com.ecommerce.domain.entity.UserCouponStatus.AVAILABLE);
        assertThat(response.expiresAt()).isAfter(now);
    }

    @Test
    @DisplayName("사용자는 본인이 보유한 쿠폰 목록을 조회할 수 있다")
    void getUserCoupons() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon1 = new Coupon("쿠폰1", DiscountType.PERCENTAGE, 10, 100, now.minusDays(1), now.plusDays(30), 30);
        Coupon coupon2 = new Coupon("쿠폰2", DiscountType.FIXED_AMOUNT, 5000, 100, now.minusDays(1), now.plusDays(30), 30);
        Coupon savedCoupon1 = couponRepository.save(coupon1);
        Coupon savedCoupon2 = couponRepository.save(coupon2);

        // 사용자에게 쿠폰 발급
        couponService.issueCoupon(new CouponIssueRequest(1L, savedCoupon1.getId()));
        couponService.issueCoupon(new CouponIssueRequest(1L, savedCoupon2.getId()));

        // when
        List<UserCouponResponse> userCoupons = couponService.getUserCoupons(1L);

        // then
        assertThat(userCoupons).hasSize(2);
        assertThat(userCoupons).extracting("userId").containsOnly(1L);
    }

    @Test
    @DisplayName("다른 사용자의 쿠폰은 조회되지 않는다")
    void getUserCoupons_OnlyOwnCoupons() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("쿠폰", DiscountType.PERCENTAGE, 10, 100, now.minusDays(1), now.plusDays(30), 30);
        Coupon savedCoupon = couponRepository.save(coupon);

        // 사용자 1, 2에게 각각 쿠폰 발급
        couponService.issueCoupon(new CouponIssueRequest(1L, savedCoupon.getId()));
        couponService.issueCoupon(new CouponIssueRequest(2L, savedCoupon.getId()));

        // when
        List<UserCouponResponse> user1Coupons = couponService.getUserCoupons(1L);
        List<UserCouponResponse> user2Coupons = couponService.getUserCoupons(2L);

        // then
        assertThat(user1Coupons).hasSize(1);
        assertThat(user1Coupons.get(0).userId()).isEqualTo(1L);

        assertThat(user2Coupons).hasSize(1);
        assertThat(user2Coupons.get(0).userId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("보유 쿠폰이 없으면 빈 리스트가 반환된다")
    void getUserCoupons_NoCoupons_ReturnsEmptyList() {
        // when
        List<UserCouponResponse> userCoupons = couponService.getUserCoupons(999L);

        // then
        assertThat(userCoupons).isEmpty();
    }

    @Test
    @DisplayName("쿠폰 발급 시 발급 횟수가 증가한다")
    void issueCoupon_IncreasesIssueCount() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("쿠폰", DiscountType.PERCENTAGE, 10, 10, now.minusDays(1), now.plusDays(30), 30);
        Coupon savedCoupon = couponRepository.save(coupon);

        // when
        couponService.issueCoupon(new CouponIssueRequest(1L, savedCoupon.getId()));
        couponService.issueCoupon(new CouponIssueRequest(2L, savedCoupon.getId()));
        couponService.issueCoupon(new CouponIssueRequest(3L, savedCoupon.getId()));

        // then
        Coupon updatedCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getCurrentIssueCount()).isEqualTo(3);
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("발급 가능 수량이 0이면 쿠폰 발급이 실패한다")
    void issueCoupon_NoRemainingQuantity_ThrowsException() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("쿠폰", DiscountType.PERCENTAGE, 10, 1, now.minusDays(1), now.plusDays(30), 30);
        Coupon savedCoupon = couponRepository.save(coupon);

        // 먼저 1개 발급하여 수량 소진
        couponService.issueCoupon(new CouponIssueRequest(1L, savedCoupon.getId()));

        // 두 번째 시도
        CouponIssueRequest request = new CouponIssueRequest(2L, savedCoupon.getId());

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(request))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(IllegalStateException.class),
                        ex -> assertThat(ex).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                )
                .hasMessageContaining("발급 가능한 수량이 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    void issueCoupon_CouponNotFound_ThrowsException() {
        // given
        CouponIssueRequest request = new CouponIssueRequest(1L, 999L);

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(request))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(IllegalArgumentException.class),
                        ex -> assertThat(ex).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                )
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("발급된 쿠폰은 유효기간이 설정된다")
    void issueCoupon_SetsExpirationDate() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "30일 유효 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                100,
                now.minusDays(1),
                now.plusDays(60),
                30  // 발급 후 30일간 유효
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        CouponIssueRequest request = new CouponIssueRequest(1L, savedCoupon.getId());

        // when
        UserCouponResponse response = couponService.issueCoupon(request);

        // then
        LocalDateTime expectedExpiry = now.plusDays(30);
        assertThat(response.expiresAt()).isAfterOrEqualTo(expectedExpiry.minusSeconds(1));
        assertThat(response.expiresAt()).isBeforeOrEqualTo(expectedExpiry.plusSeconds(1));
    }

    @Test
    @DisplayName("여러 사용자가 순차적으로 쿠폰을 발급받을 수 있다")
    void issueCoupon_MultipleUsers_Sequential() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("쿠폰", DiscountType.PERCENTAGE, 10, 5, now.minusDays(1), now.plusDays(30), 30);
        Coupon savedCoupon = couponRepository.save(coupon);

        // when
        UserCouponResponse response1 = couponService.issueCoupon(new CouponIssueRequest(1L, savedCoupon.getId()));
        UserCouponResponse response2 = couponService.issueCoupon(new CouponIssueRequest(2L, savedCoupon.getId()));
        UserCouponResponse response3 = couponService.issueCoupon(new CouponIssueRequest(3L, savedCoupon.getId()));

        // then
        assertThat(response1.userId()).isEqualTo(1L);
        assertThat(response2.userId()).isEqualTo(2L);
        assertThat(response3.userId()).isEqualTo(3L);
        Coupon updatedCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getCurrentIssueCount()).isEqualTo(3);
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(2);
    }
}
