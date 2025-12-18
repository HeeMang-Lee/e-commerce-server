package com.ecommerce.benchmark;

import com.ecommerce.config.IntegrationTestSupport;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.ecommerce.domain.service.CouponIssueResult;
import com.ecommerce.infrastructure.redis.AsyncCouponIssueService;
import com.ecommerce.infrastructure.redis.CouponQueueProcessor;
import com.ecommerce.infrastructure.redis.CouponRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 비동기 쿠폰 발급 벤치마크 테스트
 *
 * 구조: Redis Set (중복/수량 체크) + Redis List (큐) + 스케줄러 폴링 (DB 저장)
 * Kafka 전환 전 baseline 측정용
 */
class RedisCouponIssueBenchmarkTest extends IntegrationTestSupport {

    @Autowired
    private AsyncCouponIssueService asyncCouponIssueService;

    @Autowired
    private CouponQueueProcessor couponQueueProcessor;

    @Autowired
    private CouponRedisRepository couponRedisRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        couponRedisRepository.clearQueue();
    }

    @Test
    @DisplayName("[Redis] 동시 1000명 요청, 100개 한정 쿠폰 - 정확성 및 성능 측정")
    void benchmark_1000Users_100Coupons() throws InterruptedException {
        // given
        int totalUsers = 1000;
        int couponLimit = 100;

        testCoupon = createCoupon("선착순 100명 쿠폰", couponLimit);
        couponRedisRepository.initializeCoupon(testCoupon.getId());
        couponRedisRepository.cacheCouponInfo(
                testCoupon.getId(),
                testCoupon.getMaxIssueCount(),
                testCoupon.getIssueStartDate(),
                testCoupon.getIssueEndDate()
        );

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();

        // when - Phase 1: Redis 요청 처리
        long phase1Start = System.nanoTime();

        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long taskStart = System.nanoTime();

                    CouponIssueResult result = asyncCouponIssueService.issue(userId, testCoupon.getId());

                    long taskEnd = System.nanoTime();
                    synchronized (latencies) {
                        latencies.add((taskEnd - taskStart) / 1_000_000);
                    }

                    if (result == CouponIssueResult.SUCCESS) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        long phase1End = System.nanoTime();

        executor.shutdown();

        // Phase 2: 스케줄러 배치 처리 (DB 저장)
        long phase2Start = System.nanoTime();
        while (couponRedisRepository.getQueueSize() > 0) {
            couponQueueProcessor.processQueue();
        }
        long phase2End = System.nanoTime();

        // then - 결과 계산
        double phase1Ms = (phase1End - phase1Start) / 1_000_000.0;
        double phase2Ms = (phase2End - phase2Start) / 1_000_000.0;
        double totalMs = phase1Ms + phase2Ms;

        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();
        List<Long> sorted = latencies.stream().sorted().toList();
        long p50 = sorted.get(sorted.size() / 2);
        long p99 = sorted.get((int) (sorted.size() * 0.99));

        long actualIssued = userCouponRepository.findAll().stream()
                .filter(uc -> uc.getCouponId().equals(testCoupon.getId()))
                .count();

        // 결과 출력
        System.out.println("========================================");
        System.out.println("[Redis] 동시 1000명, 100개 쿠폰 벤치마크");
        System.out.println("========================================");
        System.out.println("Phase 1 (Redis 요청): " + String.format("%.2f", phase1Ms) + " ms");
        System.out.println("Phase 2 (DB 저장): " + String.format("%.2f", phase2Ms) + " ms");
        System.out.println("전체 처리 시간: " + String.format("%.2f", totalMs) + " ms");
        System.out.println("---");
        System.out.println("SUCCESS 응답: " + successCount.get());
        System.out.println("FAIL 응답: " + failCount.get());
        System.out.println("실제 DB 발급: " + actualIssued);
        System.out.println("---");
        System.out.println("평균 Latency: " + String.format("%.2f", stats.getAverage()) + " ms");
        System.out.println("P50 Latency: " + p50 + " ms");
        System.out.println("P99 Latency: " + p99 + " ms");
        System.out.println("========================================");

        // 검증 - 정확히 100명만 발급
        assertThat(actualIssued).isEqualTo(couponLimit);
        assertThat(successCount.get()).isLessThanOrEqualTo(couponLimit + 10); // race condition 허용
    }

    @Test
    @DisplayName("[Redis] 동시 5000명 요청, 500개 한정 쿠폰 - 대규모 테스트")
    void benchmark_5000Users_500Coupons() throws InterruptedException {
        // given
        int totalUsers = 5000;
        int couponLimit = 500;

        testCoupon = createCoupon("선착순 500명 쿠폰", couponLimit);
        couponRedisRepository.initializeCoupon(testCoupon.getId());
        couponRedisRepository.cacheCouponInfo(
                testCoupon.getId(),
                testCoupon.getMaxIssueCount(),
                testCoupon.getIssueStartDate(),
                testCoupon.getIssueEndDate()
        );

        ExecutorService executor = Executors.newFixedThreadPool(200);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();

        // when - Phase 1: Redis 요청 처리
        long phase1Start = System.nanoTime();

        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long taskStart = System.nanoTime();

                    CouponIssueResult result = asyncCouponIssueService.issue(userId, testCoupon.getId());

                    long taskEnd = System.nanoTime();
                    synchronized (latencies) {
                        latencies.add((taskEnd - taskStart) / 1_000_000);
                    }

                    if (result == CouponIssueResult.SUCCESS) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(60, TimeUnit.SECONDS);
        long phase1End = System.nanoTime();

        executor.shutdown();

        // Phase 2: 스케줄러 배치 처리
        long phase2Start = System.nanoTime();
        while (couponRedisRepository.getQueueSize() > 0) {
            couponQueueProcessor.processQueue();
        }
        long phase2End = System.nanoTime();

        // then
        double phase1Ms = (phase1End - phase1Start) / 1_000_000.0;
        double phase2Ms = (phase2End - phase2Start) / 1_000_000.0;
        double totalMs = phase1Ms + phase2Ms;

        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();
        List<Long> sorted = latencies.stream().sorted().toList();
        long p50 = sorted.get(sorted.size() / 2);
        long p99 = sorted.get((int) (sorted.size() * 0.99));

        long actualIssued = userCouponRepository.findAll().stream()
                .filter(uc -> uc.getCouponId().equals(testCoupon.getId()))
                .count();

        System.out.println("========================================");
        System.out.println("[Redis] 동시 5000명, 500개 쿠폰 벤치마크");
        System.out.println("========================================");
        System.out.println("Phase 1 (Redis 요청): " + String.format("%.2f", phase1Ms) + " ms");
        System.out.println("Phase 2 (DB 저장): " + String.format("%.2f", phase2Ms) + " ms");
        System.out.println("전체 처리 시간: " + String.format("%.2f", totalMs) + " ms");
        System.out.println("---");
        System.out.println("SUCCESS 응답: " + successCount.get());
        System.out.println("FAIL 응답: " + failCount.get());
        System.out.println("실제 DB 발급: " + actualIssued);
        System.out.println("---");
        System.out.println("평균 Latency: " + String.format("%.2f", stats.getAverage()) + " ms");
        System.out.println("P50 Latency: " + p50 + " ms");
        System.out.println("P99 Latency: " + p99 + " ms");
        System.out.println("TPS: " + String.format("%.2f", totalUsers / (phase1Ms / 1000)) + " /sec");
        System.out.println("========================================");

        assertThat(actualIssued).isEqualTo(couponLimit);
    }

    private Coupon createCoupon(String name, int maxCount) {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                name,
                DiscountType.PERCENTAGE,
                10,
                maxCount,
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        return couponRepository.save(coupon);
    }
}
