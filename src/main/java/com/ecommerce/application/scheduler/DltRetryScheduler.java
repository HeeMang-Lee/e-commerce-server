package com.ecommerce.application.scheduler;

import com.ecommerce.application.event.CouponIssueEvent;
import com.ecommerce.config.KafkaConfig;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.FailedEvent;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.FailedEventRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DLT(Dead Letter Topic) 재처리 스케줄러
 *
 * DLT에서 DB에 저장된 실패 이벤트를 주기적으로 재시도한다.
 * - 10초마다 체크
 * - 지수 백오프: 30초 → 1분 → 2분 → 4분...
 * - 최대 3회 재시도 후 ABANDONED 처리
 * - 토스뱅크 Kafka 메시지 스케줄러 패턴 참고
 */
@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class DltRetryScheduler {

    private final FailedEventRepository failedEventRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 10000) // 10초마다 체크 (지수 백오프 기반 재시도)
    @Transactional
    public void retryFailedEvents() {
        List<FailedEvent> retryableEvents = failedEventRepository.findRetryableEventsNow(LocalDateTime.now());

        if (retryableEvents.isEmpty()) {
            return;
        }

        log.info("[DLT 재처리] 시작: {}건", retryableEvents.size());

        int recoveredCount = 0;
        int failedCount = 0;
        int abandonedCount = 0;

        for (FailedEvent event : retryableEvents) {
            if (!event.canRetry()) {
                abandonedCount++;
                continue;
            }

            event.retry();

            try {
                if (processEvent(event)) {
                    event.markAsRecovered();
                    recoveredCount++;
                    log.info("[DLT 재처리] 성공: eventId={}", event.getId());
                } else {
                    event.markAsFailed("재처리 실패");
                    failedCount++;
                    logRetryStatus(event);
                }
            } catch (Exception e) {
                event.markAsFailed(e.getMessage());
                failedCount++;
                logRetryStatus(event);
            }

            failedEventRepository.save(event);
        }

        log.info("[DLT 재처리] 완료: recovered={}, failed={}, abandoned={}",
                recoveredCount, failedCount, abandonedCount);
    }

    private boolean processEvent(FailedEvent event) {
        if (!KafkaConfig.TOPIC_COUPON_ISSUE.equals(event.getTopic())) {
            log.warn("[DLT 재처리] 지원하지 않는 토픽: {}", event.getTopic());
            return false;
        }

        try {
            CouponIssueEvent couponEvent = objectMapper.readValue(event.getPayload(), CouponIssueEvent.class);
            return processCouponIssue(couponEvent);
        } catch (Exception e) {
            log.error("[DLT 재처리] 페이로드 파싱 실패: {}", e.getMessage());
            return false;
        }
    }

    private boolean processCouponIssue(CouponIssueEvent event) {
        Long couponId = event.couponId();
        Long userId = event.userId();

        // 테스트용 강제 실패 조건은 재처리에서는 스킵
        if (userId < 0) {
            log.warn("[DLT 재처리] 테스트용 음수 userId 스킵: userId={}", userId);
            return false;
        }

        // 멱등성 체크: 이미 발급됐으면 성공 처리
        Optional<UserCoupon> existing = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existing.isPresent()) {
            log.debug("[DLT 재처리] 이미 발급된 쿠폰: couponId={}, userId={}", couponId, userId);
            return true;
        }

        // 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            log.warn("[DLT 재처리] 쿠폰을 찾을 수 없음: couponId={}", couponId);
            return false;
        }

        // 수량 체크
        if (coupon.getCurrentIssueCount() >= coupon.getMaxIssueCount()) {
            log.debug("[DLT 재처리] 쿠폰 소진: couponId={}", couponId);
            return true; // 소진된 건 성공으로 처리 (더 이상 재시도 불필요)
        }

        // UserCoupon 저장
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
        UserCoupon userCoupon = new UserCoupon(userId, couponId, expiresAt);
        userCouponRepository.save(userCoupon);

        // 발급 카운트 증가
        coupon.issue();
        couponRepository.save(coupon);

        log.info("[DLT 재처리] 쿠폰 발급 완료: couponId={}, userId={}", couponId, userId);
        return true;
    }

    private void logRetryStatus(FailedEvent event) {
        if (event.canRetry()) {
            log.warn("[DLT 재처리] 실패: eventId={}, retryCount={}/{}, 다음 재시도={}",
                    event.getId(), event.getRetryCount(), event.getMaxRetryCount(), event.getNextRetryAt());
        } else {
            log.error("[DLT 재처리] 최대 재시도 횟수 초과 → ABANDONED: eventId={}, retryCount={}",
                    event.getId(), event.getRetryCount());
        }
    }
}
