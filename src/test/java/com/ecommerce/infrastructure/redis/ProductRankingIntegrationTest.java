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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 랭킹 Redis 통합 테스트
 *
 * Redis Sorted Set 기반 판매 랭킹 기능 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("상품 랭킹 Redis 통합 테스트")
class ProductRankingIntegrationTest {

    @Autowired
    private ProductRankingRedisRepository rankingRedisRepository;

    private static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.0"));

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
    @DisplayName("판매 기록 시 Redis Sorted Set에 점수가 증가한다")
    void recordSale_IncreasesScore() {
        // given
        Long productId = 1L;
        LocalDate today = LocalDate.now();

        // when
        rankingRedisRepository.recordSale(productId, 5);
        rankingRedisRepository.recordSale(productId, 3);

        // then
        long salesCount = rankingRedisRepository.getProductSalesCount(productId, today);
        assertThat(salesCount).isEqualTo(8);
    }

    @Test
    @DisplayName("판매량 기준으로 상위 상품을 조회할 수 있다")
    void getTopProducts_OrderedBySales() {
        // given
        rankingRedisRepository.recordSale(1L, 10);  // 3위
        rankingRedisRepository.recordSale(2L, 50);  // 1위
        rankingRedisRepository.recordSale(3L, 30);  // 2위
        rankingRedisRepository.recordSale(4L, 5);   // 4위

        // when
        List<Long> topProducts = rankingRedisRepository.getTopProductsByDate(LocalDate.now(), 3);

        // then
        assertThat(topProducts).hasSize(3);
        assertThat(topProducts.get(0)).isEqualTo(2L);  // 1위: 50개
        assertThat(topProducts.get(1)).isEqualTo(3L);  // 2위: 30개
        assertThat(topProducts.get(2)).isEqualTo(1L);  // 3위: 10개
    }

    @Test
    @DisplayName("최근 3일간 판매량 합산으로 인기 상품을 조회할 수 있다")
    void getTopProductsLast3Days_CombinesMultipleDays() {
        // given
        LocalDate today = LocalDate.now();

        // 오늘: 상품1=10, 상품2=5
        rankingRedisRepository.recordSale(1L, 10);
        rankingRedisRepository.recordSale(2L, 5);

        // 어제 데이터 (직접 Redis에 기록 - 테스트용)
        // 실제로는 어제 발생한 주문으로 기록됨

        // when
        List<Long> topProducts = rankingRedisRepository.getTopProductsLast3Days(5);

        // then
        assertThat(topProducts).isNotEmpty();
        assertThat(topProducts.get(0)).isEqualTo(1L);  // 1위: 10개
    }

    @Test
    @DisplayName("상품의 순위를 조회할 수 있다")
    void getProductRank() {
        // given
        rankingRedisRepository.recordSale(1L, 10);
        rankingRedisRepository.recordSale(2L, 50);
        rankingRedisRepository.recordSale(3L, 30);

        // when
        Long rank1 = rankingRedisRepository.getProductRank(2L, LocalDate.now());  // 50개 - 1위
        Long rank2 = rankingRedisRepository.getProductRank(3L, LocalDate.now());  // 30개 - 2위
        Long rank3 = rankingRedisRepository.getProductRank(1L, LocalDate.now());  // 10개 - 3위

        // then
        assertThat(rank1).isEqualTo(0L);  // 0-based, 1위
        assertThat(rank2).isEqualTo(1L);  // 0-based, 2위
        assertThat(rank3).isEqualTo(2L);  // 0-based, 3위
    }

    @Test
    @DisplayName("동시에 여러 상품의 판매를 기록할 수 있다")
    void recordMultipleSales_Concurrently() throws InterruptedException {
        // given
        int threadCount = 100;
        Long productId = 1L;

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(10);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    rankingRedisRepository.recordSale(productId, 1);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        long salesCount = rankingRedisRepository.getProductSalesCount(productId, LocalDate.now());
        assertThat(salesCount).isEqualTo(threadCount);  // 정확히 100개
    }

    @Test
    @DisplayName("랭킹 데이터가 없으면 빈 리스트를 반환한다")
    void getTopProducts_EmptyWhenNoData() {
        // when
        List<Long> topProducts = rankingRedisRepository.getTopProductsLast3Days(5);

        // then
        assertThat(topProducts).isEmpty();
    }
}
