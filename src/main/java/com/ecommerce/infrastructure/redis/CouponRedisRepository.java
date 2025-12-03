package com.ecommerce.infrastructure.redis;

import com.ecommerce.domain.service.CouponIssueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * 쿠폰 발급을 위한 Redis Repository
 *
 * Redis 자료구조:
 * - Set: coupon:{couponId}:issued → 발급받은 userId 목록 (중복 방지 + 수량 체크)
 * - List: coupon:queue → DB 반영 대기열
 *
 * 원자적 연산:
 * - SISMEMBER: 중복 체크 O(1)
 * - SCARD: 수량 확인 O(1)
 * - SADD: 발급 기록 O(1)
 * - RPUSH/LPOP: 대기열 관리 O(1)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String ISSUED_KEY_PREFIX = "coupon:";
    private static final String ISSUED_KEY_SUFFIX = ":issued";
    private static final String QUEUE_KEY = "coupon:queue";

    /**
     * 쿠폰 발급 시도 (Set 기반)
     *
     * 1. 이미 발급받았는지 확인 (SISMEMBER)
     * 2. 수량 확인 (SCARD)
     * 3. 발급 처리 (SADD)
     * 4. 대기열에 추가 (RPUSH)
     */
    public CouponIssueResult tryIssue(Long userId, Long couponId, int maxQuantity) {
        String issuedKey = getIssuedKey(couponId);
        String userIdStr = userId.toString();

        // 1. 이미 발급받았는지 확인
        Boolean alreadyIssued = redisTemplate.opsForSet().isMember(issuedKey, userIdStr);
        if (Boolean.TRUE.equals(alreadyIssued)) {
            return CouponIssueResult.ALREADY_ISSUED;
        }

        // 2. 현재 발급 수량 확인
        Long currentCount = redisTemplate.opsForSet().size(issuedKey);
        if (currentCount != null && currentCount >= maxQuantity) {
            return CouponIssueResult.SOLD_OUT;
        }

        // 3. 발급 처리 (SADD는 원자적)
        Long added = redisTemplate.opsForSet().add(issuedKey, userIdStr);
        if (added == null || added == 0) {
            // 이미 다른 스레드에서 추가됨
            return CouponIssueResult.ALREADY_ISSUED;
        }

        // 4. 수량 초과 체크 (SADD 후 다시 확인)
        Long countAfterAdd = redisTemplate.opsForSet().size(issuedKey);
        if (countAfterAdd != null && countAfterAdd > maxQuantity) {
            // 수량 초과 - 롤백
            redisTemplate.opsForSet().remove(issuedKey, userIdStr);
            return CouponIssueResult.SOLD_OUT;
        }

        // 5. 대기열에 추가 (DB 반영용)
        String queueData = couponId + ":" + userId;
        redisTemplate.opsForList().rightPush(QUEUE_KEY, queueData);

        log.debug("쿠폰 발급 요청 접수: couponId={}, userId={}", couponId, userId);
        return CouponIssueResult.SUCCESS;
    }

    /**
     * 발급 여부 확인
     */
    public boolean isIssued(Long userId, Long couponId) {
        String issuedKey = getIssuedKey(couponId);
        Boolean result = redisTemplate.opsForSet().isMember(issuedKey, userId.toString());
        return Boolean.TRUE.equals(result);
    }

    /**
     * 현재 발급된 수량 조회
     */
    public long getIssuedCount(Long couponId) {
        String issuedKey = getIssuedKey(couponId);
        Long count = redisTemplate.opsForSet().size(issuedKey);
        return count != null ? count : 0;
    }

    /**
     * 대기열에서 N건 가져오기 (LPOP)
     */
    public List<String> popFromQueue(int count) {
        return redisTemplate.opsForList().leftPop(QUEUE_KEY, count);
    }

    /**
     * 대기열 크기 조회
     */
    public long getQueueSize() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    /**
     * 쿠폰 발급 초기화 (수량 설정)
     * 테스트 또는 쿠폰 생성 시 호출
     */
    public void initializeCoupon(Long couponId) {
        String issuedKey = getIssuedKey(couponId);
        // 기존 데이터 삭제
        redisTemplate.delete(issuedKey);
        log.info("쿠폰 Redis 초기화: couponId={}", couponId);
    }

    /**
     * 발급된 사용자 목록 조회
     */
    public Set<String> getIssuedUsers(Long couponId) {
        String issuedKey = getIssuedKey(couponId);
        return redisTemplate.opsForSet().members(issuedKey);
    }

    /**
     * 대기열 비우기 (테스트용)
     */
    public void clearQueue() {
        redisTemplate.delete(QUEUE_KEY);
    }

    private String getIssuedKey(Long couponId) {
        return ISSUED_KEY_PREFIX + couponId + ISSUED_KEY_SUFFIX;
    }
}
