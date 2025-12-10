package com.ecommerce.application.event;

import com.ecommerce.infrastructure.redis.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 랭킹 기록 이벤트 핸들러
 *
 * 결제 완료 후 상품별 판매량을 Redis에 기록한다.
 * - AFTER_COMMIT: 메인 트랜잭션 커밋 후 실행
 * - @Async: 별도 스레드에서 비동기 실행
 * - Best Effort: Redis 실패해도 결제에 영향 없음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingEventHandler {

    private final ProductRankingService productRankingService;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
