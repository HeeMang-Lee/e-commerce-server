package com.ecommerce.application.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Spring ApplicationEventPublisher 기반 구현체
 *
 * 현재는 Spring Event를 사용하지만,
 * 추후 Kafka 등으로 전환 시 이 구현체만 교체하면 된다.
 */
@Component
@Profile("!kafka")
@RequiredArgsConstructor
public class SpringEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
}
