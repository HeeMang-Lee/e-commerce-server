package com.ecommerce.infrastructure.kafka;

import com.ecommerce.application.event.DomainEventPublisher;
import com.ecommerce.application.event.PaymentCompletedEvent;
import com.ecommerce.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publish(Object event) {
        if (event instanceof PaymentCompletedEvent e) {
            publishPaymentCompletedEvent(e);
        } else {
            log.warn("Unknown event type: {}", event.getClass().getName());
        }
    }

    private void publishPaymentCompletedEvent(PaymentCompletedEvent event) {
        String key = event.orderId().toString();

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_COMPLETED,
                key,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Kafka 메시지 발행 성공: topic={}, key={}, offset={}",
                        KafkaConfig.TOPIC_PAYMENT_COMPLETED,
                        key,
                        result.getRecordMetadata().offset());
            } else {
                log.error("Kafka 메시지 발행 실패: topic={}, key={}, error={}",
                        KafkaConfig.TOPIC_PAYMENT_COMPLETED,
                        key,
                        ex.getMessage());
            }
        });
    }
}
