package com.ecommerce.infrastructure.redis;

import com.ecommerce.config.CaffeineCacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 기반 랭킹 캐시 무효화
 *
 * 동작 방식:
 * 1. 랭킹이 업데이트되면 publish() 호출
 * 2. Redis가 모든 구독자에게 메시지 전달
 * 3. 각 서버의 MessageListener가 메시지 수신
 * 4. 로컬 Caffeine 캐시 무효화
 *
 * 이점:
 * - 분산 환경에서 모든 서버의 로컬 캐시 동기화
 * - Redis 조회 없이 로컬 캐시로 빠른 응답
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCacheInvalidator implements MessageListener {

    public static final String RANKING_CACHE_CHANNEL = "ranking:cache:invalidate";

    private final CacheManager caffeineCacheManager;
    private final StringRedisTemplate redisTemplate;

    /**
     * 캐시 무효화 메시지 발행
     * 모든 서버의 로컬 캐시를 무효화하도록 Redis Pub/Sub으로 전달
     */
    public void publishInvalidation() {
        try {
            redisTemplate.convertAndSend(RANKING_CACHE_CHANNEL, "invalidate");
            log.debug("랭킹 캐시 무효화 메시지 발행");
        } catch (Exception e) {
            log.warn("캐시 무효화 메시지 발행 실패: {}", e.getMessage());
            // 실패해도 로컬 캐시는 TTL에 의해 자연 만료됨
        }
    }

    /**
     * Pub/Sub 메시지 수신 시 로컬 캐시 무효화
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            invalidateLocalCache();
            log.debug("Pub/Sub 메시지 수신, 로컬 캐시 무효화 완료");
        } catch (Exception e) {
            log.warn("로컬 캐시 무효화 실패: {}", e.getMessage());
        }
    }

    /**
     * 로컬 Caffeine 캐시 무효화
     */
    public void invalidateLocalCache() {
        var cache = caffeineCacheManager.getCache(CaffeineCacheConfig.RANKING_CACHE);
        if (cache != null) {
            cache.clear();
            log.debug("로컬 랭킹 캐시 무효화 완료");
        }
    }
}
