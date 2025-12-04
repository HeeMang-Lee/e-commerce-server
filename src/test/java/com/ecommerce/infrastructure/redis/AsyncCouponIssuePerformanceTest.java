package com.ecommerce.infrastructure.redis;

import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.ecommerce.domain.service.CouponIssueResult;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("쿠폰 발급 성능 측정 테스트 - Redis 비동기 방식")
class AsyncCouponIssuePerformanceTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private AsyncCouponIssueService asyncCouponIssueService;

    @Autowired
    private CouponRedisRepository couponRedisRepository;

    @Autowired
    private CouponQueueProcessor couponQueueProcessor;

    private static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.0"));

    private static final String SEPARATOR = "=".repeat(60);

    @BeforeAll
    static void startContainers() {
        redis.start();
    }

    @AfterAll
    static void stopContainers() {
        redis.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @AfterEach
    void tearDown() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        couponRedisRepository.clearQueue();
    }

    @Test
    @DisplayName("[성능측정] 1000명 동시 요청, 100개 쿠폰 - Redis 비동기 방식")
    void performance_1000Users_100Coupons_Redis() throws InterruptedException {
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

        // Redis 초기화
        asyncCouponIssueService.initializeCoupon(savedCoupon.getId());

        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyIssuedCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);

        // when - Redis 발급 요청
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    CouponIssueResult result = asyncCouponIssueService.issue(userId, savedCoupon.getId());

                    switch (result) {
                        case SUCCESS -> successCount.incrementAndGet();
                        case ALREADY_ISSUED -> alreadyIssuedCount.incrementAndGet();
                        case SOLD_OUT -> soldOutCount.incrementAndGet();
                        default -> {}
                    }
                } catch (Exception e) {
                    // 예외 발생
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        executorService.shutdown();

        // then - Redis 발급 결과
        long redisTime = endTime - startTime;
        double throughput = (double) totalUsers / redisTime * 1000;
        long redisIssuedCount = asyncCouponIssueService.getIssuedCount(savedCoupon.getId());

        // 결과 출력 (Redis 요청 처리)
        printPerformanceResult(
                "Redis 비동기 방식 (요청 처리)",
                totalUsers,
                couponLimit,
                successCount.get(),
                alreadyIssuedCount.get(),
                soldOutCount.get(),
                redisTime,
                throughput,
                redisIssuedCount,
                completed
        );

        // 스케줄러로 DB 반영 처리 (실제로는 비동기로 동작)
        long queueSize = couponQueueProcessor.getQueueSize();
        System.out.println("대기열 크기: " + queueSize);

        // 검증 - Redis 수준에서 정확히 100명만 성공
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(couponLimit);
        assertThat(redisIssuedCount).isEqualTo(couponLimit);
    }

    @Test
    @DisplayName("[성능측정] 5000명 동시 요청, 500개 쿠폰 - Redis 비동기 방식")
    void performance_5000Users_500Coupons_Redis() throws InterruptedException {
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

        asyncCouponIssueService.initializeCoupon(savedCoupon.getId());

        ExecutorService executorService = Executors.newFixedThreadPool(200);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyIssuedCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    CouponIssueResult result = asyncCouponIssueService.issue(userId, savedCoupon.getId());

                    switch (result) {
                        case SUCCESS -> successCount.incrementAndGet();
                        case ALREADY_ISSUED -> alreadyIssuedCount.incrementAndGet();
                        case SOLD_OUT -> soldOutCount.incrementAndGet();
                        default -> {}
                    }
                } catch (Exception e) {
                    // 예외 발생
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
        long redisIssuedCount = asyncCouponIssueService.getIssuedCount(savedCoupon.getId());

        printPerformanceResult(
                "Redis 비동기 방식 (요청 처리)",
                totalUsers,
                couponLimit,
                successCount.get(),
                alreadyIssuedCount.get(),
                soldOutCount.get(),
                totalTime,
                throughput,
                redisIssuedCount,
                completed
        );

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(couponLimit);
        assertThat(redisIssuedCount).isEqualTo(couponLimit);
    }

    @Test
    @DisplayName("[성능측정] 10000명 동시 요청, 1000개 쿠폰 - Redis 비동기 방식")
    void performance_10000Users_1000Coupons_Redis() throws InterruptedException {
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

        asyncCouponIssueService.initializeCoupon(savedCoupon.getId());

        ExecutorService executorService = Executors.newFixedThreadPool(300);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyIssuedCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    CouponIssueResult result = asyncCouponIssueService.issue(userId, savedCoupon.getId());

                    switch (result) {
                        case SUCCESS -> successCount.incrementAndGet();
                        case ALREADY_ISSUED -> alreadyIssuedCount.incrementAndGet();
                        case SOLD_OUT -> soldOutCount.incrementAndGet();
                        default -> {}
                    }
                } catch (Exception e) {
                    // 예외 발생
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
        long redisIssuedCount = asyncCouponIssueService.getIssuedCount(savedCoupon.getId());

        printPerformanceResult(
                "Redis 비동기 방식 (요청 처리)",
                totalUsers,
                couponLimit,
                successCount.get(),
                alreadyIssuedCount.get(),
                soldOutCount.get(),
                totalTime,
                throughput,
                redisIssuedCount,
                completed
        );

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(couponLimit);
        assertThat(redisIssuedCount).isEqualTo(couponLimit);
    }

    private void printPerformanceResult(
            String testName,
            int totalUsers,
            int couponLimit,
            int successCount,
            int alreadyIssuedCount,
            int soldOutCount,
            long totalTimeMs,
            double throughput,
            long redisIssuedCount,
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
        System.out.printf("   - 발급 성공(SUCCESS): %,d 건%n", successCount);
        System.out.printf("   - 이미 발급(ALREADY_ISSUED): %,d 건%n", alreadyIssuedCount);
        System.out.printf("   - 수량 소진(SOLD_OUT): %,d 건%n", soldOutCount);
        System.out.printf("   - Redis 발급 수: %,d 건%n", redisIssuedCount);
        System.out.printf("   - 완료 여부: %s%n", completed ? "✅" : "❌ TIMEOUT");
        System.out.println(SEPARATOR);
        System.out.println();
    }
}
