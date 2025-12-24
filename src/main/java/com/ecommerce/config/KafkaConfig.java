package com.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 설정
 *
 * 재시도/DLT 처리는 각 Consumer의 @RetryableTopic 어노테이션으로 처리
 * - 지수 백오프: 1초 → 2초 → 4초
 * - 3회 실패 시 DLT 토픽으로 이동 (토픽명-dlt)
 */
@Configuration
@Profile("kafka")
public class KafkaConfig {

    public static final String TOPIC_PAYMENT_COMPLETED = "payment-completed";
    public static final String TOPIC_COUPON_ISSUE = "coupon-issue";

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_COMPLETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic couponIssueTopic() {
        return TopicBuilder.name(TOPIC_COUPON_ISSUE)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
