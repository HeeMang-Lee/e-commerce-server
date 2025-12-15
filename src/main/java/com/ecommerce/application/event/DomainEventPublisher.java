package com.ecommerce.application.event;

/**
 * 도메인 이벤트 발행 추상화 인터페이스
 *
 * ApplicationEventPublisher를 직접 사용하지 않고
 * 인터페이스를 통해 이벤트 발행을 추상화한다.
 *
 * 장점:
 * - 테스트 시 Mock 주입 용이
 * - 이벤트 발행 방식 변경 용이 (Spring Event → Kafka 등)
 * - 서비스 코드가 Spring에 직접 의존하지 않음
 */
public interface DomainEventPublisher {

    void publish(Object event);
}
