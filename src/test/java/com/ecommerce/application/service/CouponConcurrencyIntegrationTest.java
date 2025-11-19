package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 쿠폰 발급 동시성 통합 테스트
 * ExecutorService를 사용하여 멀티스레드 환경에서 동시성 제어를 검증합니다.
 * MySQL 데이터베이스와 실제 JPA repository를 사용하여 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("쿠폰 발급 동시성 통합 테스트")
class CouponConcurrencyIntegrationTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponService couponService;

    @AfterEach
    void tearDown() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
    }

    @Test
    @DisplayName("100명이 동시에 50개 쿠폰을 발급 시도하면 정확히 50명만 성공한다")
    void issueCoupon_Concurrency_50OutOf100() throws InterruptedException {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "선착순 50명 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                50,  // 최대 발급 수량 50개
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        int threadCount = 100;  // 100명이 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);  // 동시 시작을 위한 래치
        CountDownLatch doneLatch = new CountDownLatch(threadCount);  // 완료 대기용 래치

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= threadCount; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드가 동시에 시작하도록 대기

                    CouponIssueRequest request = new CouponIssueRequest(userId, savedCoupon.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();

                } catch (IllegalStateException e) {
                    // 발급 가능한 수량이 없습니다 - 예상된 예외
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 시작
        doneLatch.await(10, TimeUnit.SECONDS);  // 모든 스레드 완료 대기
        executorService.shutdown();

        // then
        Coupon resultCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(50);  // 정확히 50명만 성공
        assertThat(failCount.get()).isEqualTo(50);     // 나머지 50명은 실패
        assertThat(resultCoupon.getCurrentIssueCount()).isEqualTo(50);  // 발급 횟수 확인
        assertThat(resultCoupon.getRemainingQuantity()).isEqualTo(0);   // 남은 수량 0
    }

    @Test
    @DisplayName("동일한 사용자가 동시에 같은 쿠폰을 여러 번 발급 시도해도 1번만 성공한다")
    void issueCoupon_SameUser_OnlyOnceSuccess() throws InterruptedException {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "중복 발급 방지 테스트",
                DiscountType.PERCENTAGE,
                10,
                100,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        int threadCount = 10;  // 동일 사용자가 10번 시도
        long userId = 1L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    CouponIssueRequest request = new CouponIssueRequest(userId, savedCoupon.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // UNIQUE 제약 조건 위반 또는 중복 발급으로 실패
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        Coupon resultCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        long issuedCount = userCouponRepository.findByUserId(userId).size();

        // UNIQUE 제약 조건으로 인해 정확히 1번만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(1);  // 정확히 1번만 성공
        assertThat(failCount.get()).isEqualTo(9);     // 나머지 9번은 실패
        assertThat(issuedCount).isEqualTo(1);         // 실제로 1개만 발급됨
        assertThat(resultCoupon.getCurrentIssueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰 수량이 1개일 때 여러 명이 동시에 발급 시도하면 1명만 성공한다")
    void issueCoupon_OneCoupon_OnlyOneSuccess() throws InterruptedException {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "단 1명만",
                DiscountType.FIXED_AMOUNT,
                5000,
                1,  // 최대 발급 수량 1개
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        int threadCount = 50;  // 50명이 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= threadCount; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    CouponIssueRequest request = new CouponIssueRequest(userId, savedCoupon.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();

                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        Coupon resultCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(1);   // 정확히 1명만 성공
        assertThat(failCount.get()).isEqualTo(49);     // 나머지 49명은 실패
        assertThat(resultCoupon.getCurrentIssueCount()).isEqualTo(1);
        assertThat(resultCoupon.getRemainingQuantity()).isEqualTo(0);
    }
}
