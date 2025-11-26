package com.ecommerce.config;

import com.ecommerce.application.dto.ProductResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

/**
 * Redis Cache 설정
 *
 * 직렬화 방식: SnappyRedisSerializer (Jackson + Snappy 압축)
 * - 타입 정보(@class)를 저장하지 않아 패키지 리팩토링에 안전
 * - Snappy 압축으로 Redis 메모리 사용량 절감 (평균 30~50%)
 * - TypeReference로 제네릭 타입 지원
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String POPULAR_PRODUCTS_CACHE = "popularProducts";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // 인기 상품용 Serializer (List<ProductResponse>)
        SnappyRedisSerializer<List<ProductResponse>> popularProductsSerializer =
                new SnappyRedisSerializer<>(objectMapper, new TypeReference<>() {});

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .entryTtl(Duration.ofMinutes(5));

        /**
         * 인기 상품 캐시 설정 (Look Aside 패턴)
         *
         * TTL: 10분
         * - 3일간 판매량 기준이므로 순위 변동이 빈번하지 않음
         * - DB 조회 비용이 높음 (최근 3일 주문 데이터 GROUP BY + ORDER BY)
         * - 10분 단위 갱신으로 충분한 최신성 유지 + 높은 캐시 히트율
         * - 트래픽에 따라 자동 조절 (요청 없으면 캐시 갱신 안 됨)
         *
         * 직렬화: SnappyRedisSerializer
         * - JSON + Snappy 압축으로 메모리 사용량 30~50% 절감
         * - 타입 정보(@class) 미포함으로 패키지 리팩토링에 안전
         * - TypeReference로 List<ProductResponse> 제네릭 타입 지원
         *
         * Cache Stampede 방지:
         * - @Cacheable의 sync=true 옵션으로 동시 요청 시 한 번만 DB 조회
         */
        RedisCacheConfiguration popularProductsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(popularProductsSerializer)
                )
                .entryTtl(Duration.ofMinutes(10));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(POPULAR_PRODUCTS_CACHE, popularProductsConfig)
                .build();
    }
}
