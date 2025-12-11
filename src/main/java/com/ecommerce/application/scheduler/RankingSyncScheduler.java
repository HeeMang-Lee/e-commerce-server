package com.ecommerce.application.scheduler;

import com.ecommerce.domain.entity.OrderItem;
import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.entity.PaymentStatus;
import com.ecommerce.domain.repository.OrderItemRepository;
import com.ecommerce.domain.repository.OrderPaymentRepository;
import com.ecommerce.infrastructure.redis.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 동기화 배치 스케줄러
 *
 * 결제 완료된 주문 기반으로 Redis 랭킹 데이터를 보정한다.
 * - 이벤트 유실로 인한 랭킹 누락 복구
 * - 최근 N시간 내 결제 완료 건 기준으로 동기화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSyncScheduler {

    private static final int SYNC_HOURS = 1; // 최근 1시간 내 결제 건 대상

    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRankingService productRankingService;

    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    public void syncRankingFromCompletedPayments() {
        LocalDateTime syncAfter = LocalDateTime.now().minusHours(SYNC_HOURS);

        List<OrderPayment> completedPayments = orderPaymentRepository
                .findByStatusAndPaidAtAfter(PaymentStatus.COMPLETED, syncAfter);

        if (completedPayments.isEmpty()) {
            return;
        }

        log.info("랭킹 동기화 시작: 대상 결제 건수={}, 기준시간={}", completedPayments.size(), syncAfter);

        Map<Long, Integer> productSalesMap = aggregateSales(completedPayments);

        int syncCount = 0;
        for (Map.Entry<Long, Integer> entry : productSalesMap.entrySet()) {
            try {
                productRankingService.recordSale(entry.getKey(), entry.getValue());
                syncCount++;
            } catch (Exception e) {
                log.warn("랭킹 동기화 실패: productId={}, error={}", entry.getKey(), e.getMessage());
            }
        }

        log.info("랭킹 동기화 완료: 동기화 상품수={}", syncCount);
    }

    private Map<Long, Integer> aggregateSales(List<OrderPayment> payments) {
        List<Long> orderIds = payments.stream()
                .map(OrderPayment::getOrderId)
                .toList();

        List<OrderItem> orderItems = orderItemRepository.findByOrderIdIn(orderIds);

        Map<Long, Integer> salesMap = new HashMap<>();
        for (OrderItem item : orderItems) {
            salesMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        return salesMap;
    }
}
