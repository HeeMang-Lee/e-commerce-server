package com.ecommerce.infrastructure.redis;

import com.ecommerce.config.TestcontainersConfig;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("상품 랭킹 성능 측정 테스트")
class ProductRankingPerformanceTest {

    @Autowired
    private ProductRankingRedisRepository rankingRedisRepository;

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

    @BeforeEach
    void setUp() {
        rankingRedisRepository.clearAll();
    }

    @Test
    @DisplayName("[성능측정] 10,000건 판매 기록 - Redis ZINCRBY")
    void performance_RecordSales_10000() throws InterruptedException {
        // given
        int totalRecords = 10000;
        int productCount = 100;  // 100개 상품에 분산

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(totalRecords);

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRecords; i++) {
            final long productId = (i % productCount) + 1;
            executor.submit(() -> {
                try {
                    rankingRedisRepository.recordSale(productId, 1);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.currentTimeMillis();

        // then
        long totalTime = endTime - startTime;
        double throughput = (double) totalRecords / totalTime * 1000;

        printPerformanceResult(
                "판매 기록 (ZINCRBY)",
                totalRecords,
                totalTime,
                throughput
        );

        // 검증: 각 상품당 100건씩 기록됨
        long totalSales = 0;
        for (int i = 1; i <= productCount; i++) {
            totalSales += rankingRedisRepository.getProductSalesCount((long) i, LocalDate.now());
        }
        assertThat(totalSales).isEqualTo(totalRecords);
    }

    @Test
    @DisplayName("[성능측정] Top 5 조회 - Redis ZREVRANGE")
    void performance_GetTop5_Redis() {
        // given: 1000개 상품에 랜덤 판매량 기록
        for (int i = 1; i <= 1000; i++) {
            rankingRedisRepository.recordSale((long) i, (int) (Math.random() * 100));
        }

        int iterations = 1000;

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            rankingRedisRepository.getTopProductsByDate(LocalDate.now(), 5);
        }

        long endTime = System.currentTimeMillis();

        // then
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / iterations;

        printQueryPerformanceResult(
                "Top 5 조회 (ZREVRANGE) - Redis",
                iterations,
                totalTime,
                avgTime
        );

        assertThat(avgTime).isLessThan(10);  // 평균 10ms 미만
    }

    @Test
    @DisplayName("[성능측정] 3일 합산 Top 5 조회 - Redis ZUNIONSTORE")
    void performance_GetTop5_3Days_Redis() {
        // given: 1000개 상품에 3일치 데이터 기록 (시뮬레이션)
        for (int i = 1; i <= 1000; i++) {
            rankingRedisRepository.recordSale((long) i, (int) (Math.random() * 100));
        }

        int iterations = 100;

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            rankingRedisRepository.getTopProductsLast3Days(5);
        }

        long endTime = System.currentTimeMillis();

        // then
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / iterations;

        printQueryPerformanceResult(
                "3일 합산 Top 5 조회 (ZUNIONSTORE) - Redis",
                iterations,
                totalTime,
                avgTime
        );

        assertThat(avgTime).isLessThan(50);  // 평균 50ms 미만
    }

    private void printPerformanceResult(String operation, int totalRecords, long totalTimeMs, double throughput) {
        System.out.println();
        System.out.println(SEPARATOR);
        System.out.println("📊 성능 측정 결과: " + operation);
        System.out.println(SEPARATOR);
        System.out.printf("   - 총 기록 수: %,d 건%n", totalRecords);
        System.out.printf("   - 총 처리 시간: %,d ms%n", totalTimeMs);
        System.out.printf("   - 처리량: %.2f ops/sec%n", throughput);
        System.out.println(SEPARATOR);
        System.out.println();
    }

    private void printQueryPerformanceResult(String operation, int iterations, long totalTimeMs, double avgTimeMs) {
        System.out.println();
        System.out.println(SEPARATOR);
        System.out.println("📊 조회 성능 측정: " + operation);
        System.out.println(SEPARATOR);
        System.out.printf("   - 조회 횟수: %,d 회%n", iterations);
        System.out.printf("   - 총 시간: %,d ms%n", totalTimeMs);
        System.out.printf("   - 평균 응답 시간: %.2f ms%n", avgTimeMs);
        System.out.println(SEPARATOR);
        System.out.println();
    }
}
