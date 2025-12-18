package com.ecommerce.application.event;

import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.repository.OutboxEventRepository;
import com.ecommerce.infrastructure.external.DataPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터 플랫폼 전송 이벤트 핸들러 (별도 클래스로 Self-Invocation 해결)
 *
 * 결제 완료 후 주문 데이터를 외부 데이터 플랫폼으로 전송한다.
 * - @Async: 별도 스레드에서 비동기 실행
 * - Best Effort: 실패 시 Outbox에 저장하여 재시도
 *
 * kafka 프로파일에서는 DataPlatformKafkaConsumer가 대신 처리한다.
 */
@Slf4j
@Component
@Profile("!kafka")
@RequiredArgsConstructor
public class DataPlatformEventHandler {

    private static final String EVENT_TYPE_ORDER_COMPLETED = "ORDER_COMPLETED";

    private final DataPlatformService dataPlatformService;
    private final OutboxEventRepository outboxEventRepository;

    @Async("eventExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(PaymentCompletedEvent event) {
        log.info("데이터 플랫폼 전송 시작: orderId={}", event.orderId());

        String orderData = event.toOrderDataJson();

        try {
            boolean success = dataPlatformService.sendOrderData(orderData);
            if (success) {
                log.info("데이터 플랫폼 전송 성공: orderId={}", event.orderId());
            } else {
                log.warn("데이터 플랫폼 전송 실패, 아웃박스에 저장: orderId={}", event.orderId());
                saveToOutbox(orderData);
            }
        } catch (Exception e) {
            log.error("데이터 플랫폼 전송 중 예외 발생, 아웃박스에 저장: orderId={}", event.orderId(), e);
            saveToOutbox(orderData);
        }
    }

    private void saveToOutbox(String orderData) {
        OutboxEvent outboxEvent = new OutboxEvent(EVENT_TYPE_ORDER_COMPLETED, orderData);
        outboxEventRepository.save(outboxEvent);
    }
}
