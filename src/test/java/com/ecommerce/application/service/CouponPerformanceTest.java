package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 성능 측정 테스트
 *
 * 목적: Redis 기반 비동기 방식 도입 전후 성능 비교를 위한 Baseline 측정
 *
 * 측정 항목:
 * - 총 처리 시간 (ms)
 * - 처리량 (requests/sec)
 * - 성공/실패 건수
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("쿠폰 발급 성능 측정 테스트 - 기존 방식 (분산락 + DB)")
class CouponPerformanceTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponService couponService;

    private static final String SEPARATOR = "=".repeat(60);

    @AfterEach
    void tearDown() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
    }

    @Test
    @DisplayName("[성능측정] 1000명 동시 요청, 100개 쿠폰 - 기존 방식 (분산락 + DB)")
    void performance_1000Users_100Coupons_DistributedLock() throws InterruptedException {
        // given
        int totalUsers = 1000;
        int couponLimit = 100;

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "선착순 100명 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                couponLimit,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    CouponIssueRequest request = new CouponIssueRequest(userId, savedCoupon.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        executorService.shutdown();

        // then
        long totalTime = endTime - startTime;
        double throughput = (double) totalUsers / totalTime * 1000;

        Coupon resultCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        long actualIssued = userCouponRepository.findAll().size();

        // 결과 출력
        printPerformanceResult(
                "기존 방식 (분산락 + DB)",
                totalUsers,
                couponLimit,
                successCount.get(),
                failCount.get(),
                totalTime,
                throughput,
                actualIssued,
                completed
        );

        // 검증
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(couponLimit);
        assertThat(failCount.get()).isEqualTo(totalUsers - couponLimit);
        assertThat(resultCoupon.getCurrentIssueCount()).isEqualTo(couponLimit);
        assertThat(actualIssued).isEqualTo(couponLimit);
    }

    @Test
    @DisplayName("[성능측정] 5000명 동시 요청, 500개 쿠폰 - 기존 방식 (분산락 + DB)")
    void performance_5000Users_500Coupons_DistributedLock() throws InterruptedException {
        // given
        int totalUsers = 5000;
        int couponLimit = 500;

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "선착순 500명 쿠폰",
                DiscountType.PERCENTAGE,
                15,
                couponLimit,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        ExecutorService executorService = Executors.newFixedThreadPool(200);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    CouponIssueRequest request = new CouponIssueRequest(userId, savedCoupon.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(120, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        executorService.shutdown();

        // then
        long totalTime = endTime - startTime;
        double throughput = (double) totalUsers / totalTime * 1000;

        Coupon resultCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        long actualIssued = userCouponRepository.findAll().size();

        // 결과 출력
        printPerformanceResult(
                "기존 방식 (분산락 + DB)",
                totalUsers,
                couponLimit,
                successCount.get(),
                failCount.get(),
                totalTime,
                throughput,
                actualIssued,
                completed
        );

        // 검증
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(couponLimit);
        assertThat(failCount.get()).isEqualTo(totalUsers - couponLimit);
        assertThat(resultCoupon.getCurrentIssueCount()).isEqualTo(couponLimit);
        assertThat(actualIssued).isEqualTo(couponLimit);
    }

    @Test
    @DisplayName("[성능측정] 10000명 동시 요청, 1000개 쿠폰 - 기존 방식 (분산락 + DB)")
    void performance_10000Users_1000Coupons_DistributedLock() throws InterruptedException {
        // given
        int totalUsers = 10000;
        int couponLimit = 1000;

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "선착순 1000명 쿠폰",
                DiscountType.FIXED_AMOUNT,
                5000,
                couponLimit,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        ExecutorService executorService = Executors.newFixedThreadPool(300);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    CouponIssueRequest request = new CouponIssueRequest(userId, savedCoupon.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(180, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        executorService.shutdown();

        // then
        long totalTime = endTime - startTime;
        double throughput = (double) totalUsers / totalTime * 1000;

        Coupon resultCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        long actualIssued = userCouponRepository.findAll().size();

        // 결과 출력
        printPerformanceResult(
                "기존 방식 (분산락 + DB)",
                totalUsers,
                couponLimit,
                successCount.get(),
                failCount.get(),
                totalTime,
                throughput,
                actualIssued,
                completed
        );

        // 검증
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(couponLimit);
        assertThat(failCount.get()).isEqualTo(totalUsers - couponLimit);
        assertThat(resultCoupon.getCurrentIssueCount()).isEqualTo(couponLimit);
        assertThat(actualIssued).isEqualTo(couponLimit);
    }

    private void printPerformanceResult(
            String testName,
            int totalUsers,
            int couponLimit,
            int successCount,
            int failCount,
            long totalTimeMs,
            double throughput,
            long actualIssued,
            boolean completed
    ) {
        System.out.println();
        System.out.println(SEPARATOR);
        System.out.println("📊 성능 측정 결과: " + testName);
        System.out.println(SEPARATOR);
        System.out.println("📋 테스트 조건");
        System.out.printf("   - 동시 요청 수: %,d 명%n", totalUsers);
        System.out.printf("   - 쿠폰 수량: %,d 개%n", couponLimit);
        System.out.println();
        System.out.println("⏱️ 성능 지표");
        System.out.printf("   - 총 처리 시간: %,d ms (%.2f sec)%n", totalTimeMs, totalTimeMs / 1000.0);
        System.out.printf("   - 처리량(Throughput): %.2f req/sec%n", throughput);
        System.out.printf("   - 평균 응답 시간: %.2f ms%n", (double) totalTimeMs / totalUsers);
        System.out.println();
        System.out.println("✅ 처리 결과");
        System.out.printf("   - 성공: %,d 건%n", successCount);
        System.out.printf("   - 실패: %,d 건%n", failCount);
        System.out.printf("   - 실제 발급: %,d 건%n", actualIssued);
        System.out.printf("   - 완료 여부: %s%n", completed ? "✅" : "❌ TIMEOUT");
        System.out.println(SEPARATOR);
        System.out.println();
    }
}
