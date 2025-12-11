package com.ecommerce.application.scheduler;

import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.entity.OutboxStatus;
import com.ecommerce.domain.repository.OutboxEventRepository;
import com.ecommerce.infrastructure.external.DataPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 재처리 배치 스케줄러
 *
 * 데이터 플랫폼 전송 실패 건을 주기적으로 재시도한다.
 * - PENDING/FAILED 상태의 이벤트 조회
 * - 최대 재시도 횟수 초과 시 FAILED로 유지 (수동 처리 필요)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRetryScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final DataPlatformService dataPlatformService;

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    @Transactional
    public void retryFailedEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
        List<OutboxEvent> failedEvents = outboxEventRepository.findByStatus(OutboxStatus.FAILED);

        int totalCount = pendingEvents.size() + failedEvents.size();
        if (totalCount == 0) {
            return;
        }

        log.info("Outbox 재처리 시작: pending={}, failed={}", pendingEvents.size(), failedEvents.size());

        int successCount = 0;
        int failCount = 0;

        for (OutboxEvent event : pendingEvents) {
            if (processEvent(event)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        for (OutboxEvent event : failedEvents) {
            if (!event.canRetry(MAX_RETRY_COUNT)) {
                log.warn("최대 재시도 횟수 초과, 수동 처리 필요: eventId={}, retryCount={}",
                        event.getId(), event.getRetryCount());
                continue;
            }

            if (processEvent(event)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("Outbox 재처리 완료: success={}, fail={}", successCount, failCount);
    }

    private boolean processEvent(OutboxEvent event) {
        try {
            boolean success = dataPlatformService.sendOrderData(event.getPayload());
            if (success) {
                event.markAsProcessed();
                outboxEventRepository.save(event);
                log.debug("Outbox 이벤트 전송 성공: eventId={}", event.getId());
                return true;
            } else {
                event.markAsFailed();
                outboxEventRepository.save(event);
                log.warn("Outbox 이벤트 전송 실패: eventId={}, retryCount={}",
                        event.getId(), event.getRetryCount());
                return false;
            }
        } catch (Exception e) {
            event.markAsFailed();
            outboxEventRepository.save(event);
            log.error("Outbox 이벤트 전송 중 예외: eventId={}, error={}",
                    event.getId(), e.getMessage());
            return false;
        }
    }
}
