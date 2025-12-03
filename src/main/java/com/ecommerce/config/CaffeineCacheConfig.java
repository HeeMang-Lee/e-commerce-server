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
 * 사용 목적:
 * - 인기 상품 랭킹 조회 결과 로컬 캐싱
 * - Redis 조회 부하 감소
 * - 응답 속도 향상
 *
 * 캐시 무효화:
 * - Redis Pub/Sub를 통해 모든 서버의 로컬 캐시 동기화
 */
@Configuration
@EnableCaching
public class CaffeineCacheConfig {

    public static final String RANKING_CACHE = "rankingCache";
    public static final int CACHE_TTL_SECONDS = 60;
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
