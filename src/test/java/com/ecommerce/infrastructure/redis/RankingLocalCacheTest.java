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
 * 버전 기반 캐시 일관성 테스트 (올리브영 스타일)
 *
 * 테스트 시나리오:
 * 1. 버전 조회/증가 동작 확인
 * 2. 버전 증가 시 캐시 키 변경 확인
 * 3. 성능 측정
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("버전 기반 캐시 일관성 테스트")
class RankingLocalCacheTest {

    @Autowired
    private ProductRankingService rankingService;

    @Autowired
    private ProductRankingRedisRepository rankingRedisRepository;

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
        Cache cache = cacheManager.getCache(CaffeineCacheConfig.RANKING_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("버전 조회 - 초기값은 0")
    void getCurrentVersion_shouldReturnInitialValue() {
        // when
        long version = rankingService.getCurrentVersion();

        // then
        assertThat(version).isEqualTo(0L);
    }

    @Test
    @DisplayName("버전 증가 - 순차적으로 증가")
    void incrementVersion_shouldIncreaseSequentially() {
        // given
        long initialVersion = rankingService.getCurrentVersion();

        // when
        long newVersion1 = rankingService.incrementVersion();
        long newVersion2 = rankingService.incrementVersion();

        // then
        assertThat(newVersion1).isEqualTo(initialVersion + 1);
        assertThat(newVersion2).isEqualTo(initialVersion + 2);
    }

    @Test
    @DisplayName("버전 증가 시 새 캐시 키 생성 - 기존 캐시와 다른 키")
    void versionIncrement_shouldCreateNewCacheKey() {
        // given
        long oldVersion = rankingService.getCurrentVersion();
        String oldCacheKey = "5_" + oldVersion;

        // when
        long newVersion = rankingService.incrementVersion();
        String newCacheKey = "5_" + newVersion;

        // then - 키가 달라짐
        assertThat(newCacheKey).isNotEqualTo(oldCacheKey);
        assertThat(newVersion).isEqualTo(oldVersion + 1);
    }

    @Test
    @DisplayName("getTopProducts 호출 시 버전 기반 캐시 사용 확인")
    void getTopProducts_shouldCacheWithVersionKey() {
        // given
        rankingRedisRepository.recordSale(1L, 100);
        long version = rankingService.getCurrentVersion();

        // when - getTopProducts 호출 (self-injection으로 AOP 프록시 경유)
        rankingService.getTopProducts(5);

        // then - 버전 기반 키로 캐시됨
        Cache cache = cacheManager.getCache(CaffeineCacheConfig.RANKING_CACHE);
        assertThat(cache).isNotNull();

        // 캐시 키 형식: "limit_version"
        String expectedKey = "5_" + version;
        Cache.ValueWrapper wrapper = cache.get(expectedKey);
        assertThat(wrapper).isNotNull();
    }

    @Test
    @DisplayName("[성능측정] 버전 조회는 매우 빠름 (숫자 하나)")
    void versionQuery_shouldBeFast() {
        // given
        int iterations = 10000;

        // when
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            rankingService.getCurrentVersion();
        }
        long totalTime = System.currentTimeMillis() - startTime;

        // then
        double avgTime = (double) totalTime / iterations;
        System.out.println();
        System.out.println("============================================================");
        System.out.println("📊 버전 조회 성능");
        System.out.println("============================================================");
        System.out.printf("   - 조회 횟수: %,d 회%n", iterations);
        System.out.printf("   - 총 시간: %,d ms%n", totalTime);
        System.out.printf("   - 평균 응답 시간: %.4f ms%n", avgTime);
        System.out.println("============================================================");
        System.out.println();

        assertThat(avgTime).isLessThan(1);  // 평균 1ms 미만
    }

    @Test
    @DisplayName("[성능측정] 캐시 히트 vs 미스 성능 비교")
    void performance_cacheHitVsMiss() {
        // given
        for (int i = 1; i <= 100; i++) {
            rankingRedisRepository.recordSale((long) i, (int) (Math.random() * 100));
        }

        int iterations = 100;

        // 캐시 미스 시뮬레이션 (매번 버전 증가)
        long missStartTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            rankingService.incrementVersion();  // 버전 변경으로 캐시 미스 유발
            rankingService.getTopProducts(5);
        }
        long missTime = System.currentTimeMillis() - missStartTime;

        // 캐시 히트 시뮬레이션 (같은 버전 유지)
        rankingService.getTopProducts(5);  // 첫 조회로 캐시 저장
        long hitStartTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            rankingService.getTopProducts(5);  // 캐시에서 조회
        }
        long hitTime = System.currentTimeMillis() - hitStartTime;

        // 결과 출력
        System.out.println();
        System.out.println("============================================================");
        System.out.println("📊 버전 기반 캐시 성능 비교 (올리브영 스타일)");
        System.out.println("============================================================");
        System.out.printf("   - 조회 횟수: %,d 회%n", iterations);
        System.out.printf("   - 캐시 미스 (매번 버전 증가): %,d ms (평균 %.2f ms/req)%n", missTime, (double) missTime / iterations);
        System.out.printf("   - 캐시 히트 (같은 버전): %,d ms (평균 %.2f ms/req)%n", hitTime, (double) hitTime / iterations);
        if (hitTime > 0) {
            System.out.printf("   - 성능 향상: %.1f배%n", (double) missTime / hitTime);
        }
        System.out.println("============================================================");
        System.out.println();

        // 캐시 히트가 미스보다 빠름
        assertThat(hitTime).isLessThanOrEqualTo(missTime);
    }
}
