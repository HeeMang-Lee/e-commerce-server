package com.ecommerce.application.service;

import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.entity.OutboxStatus;
import com.ecommerce.domain.repository.OutboxEventRepository;
import com.ecommerce.infrastructure.external.DataPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 아웃박스 이벤트 발행 서비스
 * PENDING 상태의 이벤트를 외부 시스템으로 전송합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final DataPlatformService dataPlatformService;

    /**
     * PENDING 상태의 이벤트를 처리합니다.
     */
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            try {
                boolean success = dataPlatformService.sendOrderData(event.getPayload());

                if (success) {
                    event.markAsProcessed();
                    log.info("이벤트 전송 성공: eventId={}, type={}", event.getId(), event.getEventType());
                } else {
                    event.markAsFailed();
                    log.warn("이벤트 전송 실패: eventId={}, retryCount={}", event.getId(), event.getRetryCount());
                }

                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("이벤트 처리 중 예외 발생: eventId={}", event.getId(), e);
                event.markAsFailed();
                outboxEventRepository.save(event);
            }
        }
    }
}
