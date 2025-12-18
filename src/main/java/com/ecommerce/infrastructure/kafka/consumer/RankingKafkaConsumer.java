package com.ecommerce.infrastructure.kafka.consumer;

import com.ecommerce.application.event.PaymentCompletedEvent;
import com.ecommerce.config.KafkaConfig;
import com.ecommerce.infrastructure.redis.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class RankingKafkaConsumer {

    private final ProductRankingService productRankingService;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_PAYMENT_COMPLETED,
            groupId = "ranking-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(PaymentCompletedEvent event) {
        log.info("Kafka 랭킹 기록 시작: orderId={}, items={}",
                event.orderId(), event.orderItems().size());

        for (PaymentCompletedEvent.OrderItemInfo item : event.orderItems()) {
            try {
                productRankingService.recordSale(item.productId(), item.quantity());
            } catch (Exception e) {
                log.warn("랭킹 기록 실패: productId={}, error={}",
                        item.productId(), e.getMessage());
            }
        }

        log.info("Kafka 랭킹 기록 완료: orderId={}", event.orderId());
    }
}
