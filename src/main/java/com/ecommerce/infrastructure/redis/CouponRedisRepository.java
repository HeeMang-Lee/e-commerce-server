package com.ecommerce.infrastructure.redis;

import com.ecommerce.domain.service.CouponIssueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String ISSUED_KEY_PREFIX = "coupon:";
    private static final String ISSUED_KEY_SUFFIX = ":issued";
    private static final String QUEUE_KEY = "coupon:queue";

    public CouponIssueResult tryIssue(Long userId, Long couponId, int maxQuantity) {
        String issuedKey = getIssuedKey(couponId);
        String userIdStr = userId.toString();

        Boolean alreadyIssued = redisTemplate.opsForSet().isMember(issuedKey, userIdStr);
        if (Boolean.TRUE.equals(alreadyIssued)) {
            return CouponIssueResult.ALREADY_ISSUED;
        }

        Long currentCount = redisTemplate.opsForSet().size(issuedKey);
        if (currentCount != null && currentCount >= maxQuantity) {
            return CouponIssueResult.SOLD_OUT;
        }

        Long added = redisTemplate.opsForSet().add(issuedKey, userIdStr);
        if (added == null || added == 0) {
            return CouponIssueResult.ALREADY_ISSUED;
        }

        Long countAfterAdd = redisTemplate.opsForSet().size(issuedKey);
        if (countAfterAdd != null && countAfterAdd > maxQuantity) {
            redisTemplate.opsForSet().remove(issuedKey, userIdStr);
            return CouponIssueResult.SOLD_OUT;
        }

        String queueData = couponId + ":" + userId;
        redisTemplate.opsForList().rightPush(QUEUE_KEY, queueData);

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

    private String getIssuedKey(Long couponId) {
        return ISSUED_KEY_PREFIX + couponId + ISSUED_KEY_SUFFIX;
    }
}
