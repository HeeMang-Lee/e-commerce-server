package com.ecommerce.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 로컬 캐시 설정
 *
 * 버전 기반 캐시 일관성 패턴:
 * - 버전이 바뀌면 새 캐시 키로 조회되어 자연스럽게 무효화
 * - TTL은 오래된 버전 캐시 메모리 정리용 (10분)
 *
 * TTL 10분 선정 이유:
 * - 3일간 인기 상품 Top 5는 천천히 변하는 데이터
 * - 1-2건 판매로 순위 변동 없음, 10분 지연도 무의미
 * - TTL 길게 → 로컬 캐시 히트율 ↑ → Redis 부하 ↓
 *
 * 캐시 키 구조: "limit_version" (예: "5_3")
 */
@Configuration
@EnableCaching
public class CaffeineCacheConfig {

    public static final String RANKING_CACHE = "rankingCache";
    public static final int CACHE_TTL_SECONDS = 600;  // 10분 (메모리 정리용)
    public static final int CACHE_MAX_SIZE = 100;

    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(RANKING_CACHE);
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(CACHE_MAX_SIZE)
                .recordStats();
    }
}
