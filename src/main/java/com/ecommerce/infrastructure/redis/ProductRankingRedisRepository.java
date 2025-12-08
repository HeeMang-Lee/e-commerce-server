package com.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductRankingRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String DAILY_RANKING_PREFIX = "ranking:daily:";
    private static final String COMBINED_RANKING_KEY = "ranking:combined:3days";
    private static final String VERSION_KEY = "ranking:version";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration DAILY_KEY_TTL = Duration.ofDays(4);  // 3일 + 여유 1일

    public void recordSale(Long productId, int quantity) {
        String todayKey = getDailyKey(LocalDate.now());
        redisTemplate.opsForZSet().incrementScore(todayKey, productId.toString(), quantity);
        redisTemplate.expire(todayKey, DAILY_KEY_TTL);

        log.debug("판매 기록: productId={}, quantity={}, key={}", productId, quantity, todayKey);
    }

    public List<Long> getTopProductsLast3Days(int limit) {
        LocalDate today = LocalDate.now();

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            keys.add(getDailyKey(today.minusDays(i)));
        }

        redisTemplate.opsForZSet().unionAndStore(
                keys.get(0),
                keys.subList(1, keys.size()),
                COMBINED_RANKING_KEY
        );

        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet()
                .reverseRangeWithScores(COMBINED_RANKING_KEY, 0, limit - 1);

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        redisTemplate.delete(COMBINED_RANKING_KEY);

        return result.stream()
                .map(tuple -> Long.parseLong(tuple.getValue()))
                .collect(Collectors.toList());
    }

    public List<Long> getTopProductsByDate(LocalDate date, int limit) {
        String key = getDailyKey(date);

        Set<String> result = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    public long getProductSalesCount(Long productId, LocalDate date) {
        String key = getDailyKey(date);
        Double score = redisTemplate.opsForZSet().score(key, productId.toString());
        return score != null ? score.longValue() : 0;
    }

    public Long getProductRank(Long productId, LocalDate date) {
        String key = getDailyKey(date);
        return redisTemplate.opsForZSet().reverseRank(key, productId.toString());
    }

    public void clearDailyRanking(LocalDate date) {
        String key = getDailyKey(date);
        redisTemplate.delete(key);
    }

    public void clearAll() {
        Set<String> keys = redisTemplate.keys(DAILY_RANKING_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(COMBINED_RANKING_KEY);
        redisTemplate.delete(VERSION_KEY);
    }

    public long getCurrentVersion() {
        String version = redisTemplate.opsForValue().get(VERSION_KEY);
        return version != null ? Long.parseLong(version) : 0L;
    }

    public long incrementVersion() {
        Long newVersion = redisTemplate.opsForValue().increment(VERSION_KEY);
        log.info("랭킹 버전 증가: {}", newVersion);
        return newVersion != null ? newVersion : 1L;
    }

    private String getDailyKey(LocalDate date) {
        return DAILY_RANKING_PREFIX + date.format(DATE_FORMAT);
    }
}
