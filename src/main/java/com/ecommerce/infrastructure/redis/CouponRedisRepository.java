package com.ecommerce.infrastructure.redis;

import com.ecommerce.domain.service.CouponIssueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String ISSUED_KEY_PREFIX = "coupon:";
    private static final String ISSUED_KEY_SUFFIX = ":issued";
    private static final String INFO_KEY_SUFFIX = ":info";
    private static final String QUEUE_KEY = "coupon:queue";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Lua Script로 원자적 쿠폰 발급 처리
     * - SISMEMBER, SCARD, SADD를 하나의 원자적 연산으로 실행
     * - race condition 완전 제거
     */
    private static final String ISSUE_SCRIPT = """
            local issuedKey = KEYS[1]
            local userId = ARGV[1]
            local maxQuantity = tonumber(ARGV[2])

            if redis.call('SISMEMBER', issuedKey, userId) == 1 then
                return 'ALREADY_ISSUED'
            end

            if redis.call('SCARD', issuedKey) >= maxQuantity then
                return 'SOLD_OUT'
            end

            redis.call('SADD', issuedKey, userId)
            return 'SUCCESS'
            """;

    private static final DefaultRedisScript<String> ISSUE_REDIS_SCRIPT;

    static {
        ISSUE_REDIS_SCRIPT = new DefaultRedisScript<>();
        ISSUE_REDIS_SCRIPT.setScriptText(ISSUE_SCRIPT);
        ISSUE_REDIS_SCRIPT.setResultType(String.class);
    }

    public CouponIssueResult tryIssue(Long userId, Long couponId, int maxQuantity) {
        return tryIssue(userId, couponId, maxQuantity, true);
    }

    public CouponIssueResult tryIssue(Long userId, Long couponId, int maxQuantity, boolean pushToQueue) {
        String issuedKey = getIssuedKey(couponId);
        String userIdStr = userId.toString();

        String result = redisTemplate.execute(
                ISSUE_REDIS_SCRIPT,
                Collections.singletonList(issuedKey),
                userIdStr,
                String.valueOf(maxQuantity)
        );

        if ("ALREADY_ISSUED".equals(result)) {
            return CouponIssueResult.ALREADY_ISSUED;
        }
        if ("SOLD_OUT".equals(result)) {
            return CouponIssueResult.SOLD_OUT;
        }

        if (pushToQueue) {
            String queueData = couponId + ":" + userId;
            redisTemplate.opsForList().rightPush(QUEUE_KEY, queueData);
        }

        log.debug("쿠폰 발급 요청 접수: couponId={}, userId={}", couponId, userId);
        return CouponIssueResult.SUCCESS;
    }

    public boolean isIssued(Long userId, Long couponId) {
        String issuedKey = getIssuedKey(couponId);
        Boolean result = redisTemplate.opsForSet().isMember(issuedKey, userId.toString());
        return Boolean.TRUE.equals(result);
    }

    public long getIssuedCount(Long couponId) {
        String issuedKey = getIssuedKey(couponId);
        Long count = redisTemplate.opsForSet().size(issuedKey);
        return count != null ? count : 0;
    }

    public List<String> popFromQueue(int count) {
        return redisTemplate.opsForList().leftPop(QUEUE_KEY, count);
    }

    public long getQueueSize() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    public void initializeCoupon(Long couponId) {
        String issuedKey = getIssuedKey(couponId);
        redisTemplate.delete(issuedKey);
        log.info("쿠폰 Redis 초기화: couponId={}", couponId);
    }

    public Set<String> getIssuedUsers(Long couponId) {
        String issuedKey = getIssuedKey(couponId);
        return redisTemplate.opsForSet().members(issuedKey);
    }

    public void clearQueue() {
        redisTemplate.delete(QUEUE_KEY);
    }

    public void cacheCouponInfo(Long couponId, int maxQuantity, LocalDateTime issueStartAt, LocalDateTime issueEndAt) {
        String infoKey = getInfoKey(couponId);
        Map<String, String> info = Map.of(
                "maxQuantity", String.valueOf(maxQuantity),
                "issueStartAt", issueStartAt.format(DATE_TIME_FORMAT),
                "issueEndAt", issueEndAt.format(DATE_TIME_FORMAT)
        );
        redisTemplate.opsForHash().putAll(infoKey, info);

        Duration ttl = Duration.between(LocalDateTime.now(), issueEndAt).plusDays(1);
        if (!ttl.isNegative()) {
            redisTemplate.expire(infoKey, ttl);
        }
        log.debug("쿠폰 정보 캐싱: couponId={}, maxQuantity={}", couponId, maxQuantity);
    }

    public CouponInfo getCouponInfo(Long couponId) {
        String infoKey = getInfoKey(couponId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(infoKey);

        if (entries.isEmpty()) {
            return null;
        }

        int maxQuantity = Integer.parseInt((String) entries.get("maxQuantity"));
        LocalDateTime issueStartAt = LocalDateTime.parse((String) entries.get("issueStartAt"), DATE_TIME_FORMAT);
        LocalDateTime issueEndAt = LocalDateTime.parse((String) entries.get("issueEndAt"), DATE_TIME_FORMAT);

        return new CouponInfo(maxQuantity, issueStartAt, issueEndAt);
    }

    public record CouponInfo(int maxQuantity, LocalDateTime issueStartAt, LocalDateTime issueEndAt) {
        public boolean canIssue() {
            LocalDateTime now = LocalDateTime.now();
            return !now.isBefore(issueStartAt) && !now.isAfter(issueEndAt);
        }
    }

    private String getIssuedKey(Long couponId) {
        return ISSUED_KEY_PREFIX + couponId + ISSUED_KEY_SUFFIX;
    }

    private String getInfoKey(Long couponId) {
        return ISSUED_KEY_PREFIX + couponId + INFO_KEY_SUFFIX;
    }
}
