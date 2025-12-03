package com.ecommerce.infrastructure.redis;

import com.ecommerce.config.CaffeineCacheConfig;
import com.ecommerce.config.TestcontainersConfig;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 캐시 + Redis Pub/Sub 캐시 무효화 테스트
 *
 * 테스트 시나리오:
 * 1. 첫 조회 시 Redis에서 가져와 로컬 캐시에 저장
 * 2. 두 번째 조회 시 로컬 캐시에서 반환 (Redis 미접근)
 * 3. Pub/Sub 무효화 메시지 수신 시 로컬 캐시 삭제
 * 4. 다음 조회 시 다시 Redis에서 가져옴
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("로컬 캐시 + Pub/Sub 무효화 테스트")
class RankingLocalCacheTest {

    @Autowired
    private ProductRankingService rankingService;

    @Autowired
    private ProductRankingRedisRepository rankingRedisRepository;

    @Autowired
    private RankingCacheInvalidator cacheInvalidator;

    @Autowired
    private CacheManager cacheManager;

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
        cacheInvalidator.invalidateLocalCache();
    }

    @Test
    @DisplayName("첫 조회 시 Redis에서 가져와 로컬 캐시에 저장")
    void firstQuery_shouldCacheLocally() {
        // given
        rankingRedisRepository.recordSale(1L, 100);
        rankingRedisRepository.recordSale(2L, 50);

        // when - 첫 조회
        rankingService.getTopProducts(5);

        // then - 로컬 캐시에 저장됨
        Cache cache = cacheManager.getCache(CaffeineCacheConfig.RANKING_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.get(5)).isNotNull();
    }

    @Test
    @DisplayName("Pub/Sub 무효화 메시지로 로컬 캐시 삭제")
    void pubSubInvalidation_shouldClearLocalCache() {
        // given - 캐시에 데이터 존재
        rankingRedisRepository.recordSale(1L, 100);
        rankingService.getTopProducts(5);

        Cache cache = cacheManager.getCache(CaffeineCacheConfig.RANKING_CACHE);
        assertThat(cache.get(5)).isNotNull();

        // when - Pub/Sub 무효화 발행
        cacheInvalidator.publishInvalidation();

        // 메시지 처리 대기
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // then - 로컬 캐시 삭제됨
        assertThat(cache.get(5)).isNull();
    }

    @Test
    @DisplayName("invalidateLocalCache 직접 호출 시 캐시 삭제")
    void invalidateLocalCache_shouldClearCache() {
        // given - 캐시에 데이터 존재
        rankingRedisRepository.recordSale(1L, 100);
        rankingService.getTopProducts(5);

        Cache cache = cacheManager.getCache(CaffeineCacheConfig.RANKING_CACHE);
        assertThat(cache.get(5)).isNotNull();

        // when
        cacheInvalidator.invalidateLocalCache();

        // then
        assertThat(cache.get(5)).isNull();
    }

    @Test
    @DisplayName("clearRanking 호출 시 로컬 캐시도 함께 무효화")
    void clearRanking_shouldInvalidateLocalCache() {
        // given
        rankingRedisRepository.recordSale(1L, 100);
        rankingService.getTopProducts(5);

        Cache cache = cacheManager.getCache(CaffeineCacheConfig.RANKING_CACHE);
        assertThat(cache.get(5)).isNotNull();

        // when
        rankingService.clearRanking();

        // 메시지 처리 대기
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // then
        assertThat(cache.get(5)).isNull();
    }

    @Test
    @DisplayName("[성능측정] 로컬 캐시 vs Redis 직접 조회")
    void performance_localCacheVsRedis() {
        // given - 1000개 상품 데이터
        for (int i = 1; i <= 1000; i++) {
            rankingRedisRepository.recordSale((long) i, (int) (Math.random() * 100));
        }

        int iterations = 1000;

        // Redis 직접 조회 (캐시 무효화 후)
        cacheInvalidator.invalidateLocalCache();
        long redisStartTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            cacheInvalidator.invalidateLocalCache();  // 매번 캐시 무효화
            rankingService.getTopProducts(5);
        }
        long redisTime = System.currentTimeMillis() - redisStartTime;

        // 로컬 캐시 조회 (첫 조회 후 캐시 사용)
        cacheInvalidator.invalidateLocalCache();
        rankingService.getTopProducts(5);  // 캐시에 저장
        long cacheStartTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            rankingService.getTopProducts(5);  // 캐시에서 조회
        }
        long cacheTime = System.currentTimeMillis() - cacheStartTime;

        // 결과 출력
        System.out.println();
        System.out.println("============================================================");
        System.out.println("📊 로컬 캐시 vs Redis 직접 조회 성능 비교");
        System.out.println("============================================================");
        System.out.printf("   - 조회 횟수: %,d 회%n", iterations);
        System.out.printf("   - Redis 직접 조회: %,d ms (평균 %.2f ms/req)%n", redisTime, (double) redisTime / iterations);
        System.out.printf("   - 로컬 캐시 조회: %,d ms (평균 %.2f ms/req)%n", cacheTime, (double) cacheTime / iterations);
        System.out.printf("   - 성능 향상: %.1f배%n", (double) redisTime / cacheTime);
        System.out.println("============================================================");
        System.out.println();

        // 로컬 캐시가 Redis보다 빠름
        assertThat(cacheTime).isLessThan(redisTime);
    }
}
