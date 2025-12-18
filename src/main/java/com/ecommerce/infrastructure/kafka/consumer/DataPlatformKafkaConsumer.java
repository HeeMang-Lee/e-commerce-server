package com.ecommerce.infrastructure.kafka.consumer;

import com.ecommerce.application.event.PaymentCompletedEvent;
import com.ecommerce.config.KafkaConfig;
import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.repository.OutboxEventRepository;
import com.ecommerce.infrastructure.external.DataPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class DataPlatformKafkaConsumer {

    private static final String EVENT_TYPE_ORDER_COMPLETED = "ORDER_COMPLETED";

    private final DataPlatformService dataPlatformService;
    private final OutboxEventRepository outboxEventRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
            topics = KafkaConfig.TOPIC_PAYMENT_COMPLETED,
            groupId = "data-platform-service"
    )
    @Transactional
    public void consume(PaymentCompletedEvent event) {
        log.info("Kafka 데이터 플랫폼 전송 시작: orderId={}", event.orderId());

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

    @DltHandler
    public void handleDlt(PaymentCompletedEvent event) {
        log.error("[DLT] 데이터 플랫폼 전송 최종 실패 - orderId={} | 수동 처리 필요", event.orderId());
        // Outbox에 저장하여 배치로 재처리 가능하게
        saveToOutbox(event.toOrderDataJson());
    }
}
