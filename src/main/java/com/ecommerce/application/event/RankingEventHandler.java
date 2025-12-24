package com.ecommerce.application.event;

import com.ecommerce.infrastructure.redis.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 랭킹 기록 이벤트 핸들러 (별도 클래스로 Self-Invocation 해결)
 *
 * 결제 완료 후 상품별 판매량을 Redis에 기록한다.
 * - @Async: 별도 스레드에서 비동기 실행
 * - Best Effort: Redis 실패해도 결제에 영향 없음
 *
 * kafka 프로파일에서는 RankingKafkaConsumer가 대신 처리한다.
 */
@Slf4j
@Component
@Profile("!kafka")
@RequiredArgsConstructor
public class RankingEventHandler {

    private final ProductRankingService productRankingService;

    @Async("eventExecutor")
    @EventListener
    public void handle(PaymentCompletedEvent event) {
        log.info("랭킹 기록 시작: orderId={}, items={}", event.orderId(), event.orderItems().size());

        for (PaymentCompletedEvent.OrderItemInfo item : event.orderItems()) {
            try {
                productRankingService.recordSale(item.productId(), item.quantity());
            } catch (Exception e) {
                log.warn("랭킹 기록 실패 (결제는 성공): productId={}, error={}",
                        item.productId(), e.getMessage());
            }
        }

        log.info("랭킹 기록 완료: orderId={}", event.orderId());
    }
}
