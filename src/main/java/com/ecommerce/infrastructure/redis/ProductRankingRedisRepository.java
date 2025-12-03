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

/**
 * 상품 판매 랭킹을 위한 Redis Repository
 *
 * 버전 기반 캐시 일관성 패턴 (올리브영 스타일):
 * - ranking:version → 현재 버전 번호
 * - 버전 증가 시 모든 서버의 로컬 캐시가 자연스럽게 무효화됨
 *
 * Redis 자료구조: Sorted Set (ZSET)
 * - Key: ranking:daily:{yyyyMMdd} (일별 랭킹)
 * - Score: 판매 수량
 * - Member: 상품 ID
 *
 * 핵심 연산:
 * - ZINCRBY: 판매 시 점수 증가 O(log N)
 * - ZREVRANGE: 상위 N개 조회 O(log N + M)
 * - ZUNIONSTORE: 여러 일자 합산 O(N) + O(M log M)
 */
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

    /**
     * 상품 판매 기록 (판매 시 호출)
     *
     * @param productId 상품 ID
     * @param quantity 판매 수량
     */
    public void recordSale(Long productId, int quantity) {
        String todayKey = getDailyKey(LocalDate.now());
        redisTemplate.opsForZSet().incrementScore(todayKey, productId.toString(), quantity);

        // TTL 설정 (처음 기록 시에만)
        if (Boolean.FALSE.equals(redisTemplate.hasKey(todayKey + ":ttl_set"))) {
            redisTemplate.expire(todayKey, DAILY_KEY_TTL);
            redisTemplate.opsForValue().set(todayKey + ":ttl_set", "1", DAILY_KEY_TTL);
        }

        log.debug("판매 기록: productId={}, quantity={}, key={}", productId, quantity, todayKey);
    }

    /**
     * 최근 3일간 인기 상품 Top N 조회
     *
     * @param limit 조회할 상품 수
     * @return 상품 ID 목록 (판매량 순)
     */
    public List<Long> getTopProductsLast3Days(int limit) {
        LocalDate today = LocalDate.now();

        // 최근 3일간의 키 목록
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            keys.add(getDailyKey(today.minusDays(i)));
        }

        // ZUNIONSTORE로 3일치 합산
        redisTemplate.opsForZSet().unionAndStore(
                keys.get(0),
                keys.subList(1, keys.size()),
                COMBINED_RANKING_KEY
        );

        // 합산된 결과에서 Top N 조회
        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet()
                .reverseRangeWithScores(COMBINED_RANKING_KEY, 0, limit - 1);

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        // 임시 키 삭제
        redisTemplate.delete(COMBINED_RANKING_KEY);

        return result.stream()
                .map(tuple -> Long.parseLong(tuple.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 특정 일자의 상품 랭킹 조회
     */
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

    /**
     * 특정 상품의 판매량 조회
     */
    public long getProductSalesCount(Long productId, LocalDate date) {
        String key = getDailyKey(date);
        Double score = redisTemplate.opsForZSet().score(key, productId.toString());
        return score != null ? score.longValue() : 0;
    }

    /**
     * 특정 상품의 순위 조회 (0-based)
     */
    public Long getProductRank(Long productId, LocalDate date) {
        String key = getDailyKey(date);
        return redisTemplate.opsForZSet().reverseRank(key, productId.toString());
    }

    /**
     * 일별 랭킹 키 초기화 (테스트용)
     */
    public void clearDailyRanking(LocalDate date) {
        String key = getDailyKey(date);
        redisTemplate.delete(key);
        redisTemplate.delete(key + ":ttl_set");
    }

    /**
     * 모든 랭킹 데이터 초기화 (테스트용)
     */
    public void clearAll() {
        Set<String> keys = redisTemplate.keys(DAILY_RANKING_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(COMBINED_RANKING_KEY);
        redisTemplate.delete(VERSION_KEY);
    }

    /**
     * 현재 랭킹 버전 조회
     *
     * @return 현재 버전 (없으면 0)
     */
    public long getCurrentVersion() {
        String version = redisTemplate.opsForValue().get(VERSION_KEY);
        return version != null ? Long.parseLong(version) : 0L;
    }

    /**
     * 랭킹 버전 증가
     * 호출 시 모든 서버의 로컬 캐시가 자연스럽게 무효화됨 (새 버전 키로 조회)
     *
     * Redis INCR: 키 없으면 0에서 시작하여 1 반환
     *
     * @return 증가된 버전
     */
    public long incrementVersion() {
        Long newVersion = redisTemplate.opsForValue().increment(VERSION_KEY);
        log.info("랭킹 버전 증가: {}", newVersion);
        return newVersion != null ? newVersion : 1L;
    }

    private String getDailyKey(LocalDate date) {
        return DAILY_RANKING_PREFIX + date.format(DATE_FORMAT);
    }
}
