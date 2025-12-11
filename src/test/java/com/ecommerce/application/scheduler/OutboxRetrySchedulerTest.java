package com.ecommerce.application.scheduler;

import com.ecommerce.config.IntegrationTestSupport;
import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.entity.OutboxStatus;
import com.ecommerce.domain.repository.OutboxEventRepository;
import com.ecommerce.infrastructure.external.DataPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("Outbox 재처리 배치 테스트")
class OutboxRetrySchedulerTest extends IntegrationTestSupport {

    @Autowired
    private OutboxRetryScheduler outboxRetryScheduler;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private DataPlatformService dataPlatformService;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("PENDING 상태의 이벤트가 재처리되어 PROCESSED로 변경된다")
    void pendingEvents_shouldBeProcessed() {
        // given
        OutboxEvent event = new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":1}");
        outboxEventRepository.save(event);

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when
        outboxRetryScheduler.retryFailedEvents();

        // then
        List<OutboxEvent> processedEvents = outboxEventRepository.findByStatus(OutboxStatus.PROCESSED);
        assertThat(processedEvents).hasSize(1);
        assertThat(processedEvents.get(0).getPayload()).isEqualTo("{\"orderId\":1}");
    }

    @Test
    @DisplayName("전송 실패 시 FAILED 상태로 변경되고 retryCount가 증가한다")
    void whenSendFails_statusShouldBeFailed() {
        // given
        OutboxEvent event = new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":2}");
        outboxEventRepository.save(event);

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(false);

        // when
        outboxRetryScheduler.retryFailedEvents();

        // then
        List<OutboxEvent> failedEvents = outboxEventRepository.findByStatus(OutboxStatus.FAILED);
        assertThat(failedEvents).hasSize(1);
        assertThat(failedEvents.get(0).getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("FAILED 상태의 이벤트도 재시도하여 성공하면 PROCESSED로 변경된다")
    void failedEvents_canBeRetried() {
        // given
        OutboxEvent event = new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":3}");
        event.markAsFailed(); // 1회 실패 상태
        outboxEventRepository.save(event);

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when
        outboxRetryScheduler.retryFailedEvents();

        // then
        List<OutboxEvent> processedEvents = outboxEventRepository.findByStatus(OutboxStatus.PROCESSED);
        assertThat(processedEvents).hasSize(1);
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 더 이상 재시도하지 않는다")
    void maxRetryExceeded_shouldNotRetry() {
        // given
        OutboxEvent event = new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":4}");
        event.markAsFailed(); // 1회
        event.markAsFailed(); // 2회
        event.markAsFailed(); // 3회 (MAX_RETRY_COUNT)
        outboxEventRepository.save(event);

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when
        outboxRetryScheduler.retryFailedEvents();

        // then - 재시도 안 됨, 여전히 FAILED
        List<OutboxEvent> failedEvents = outboxEventRepository.findByStatus(OutboxStatus.FAILED);
        assertThat(failedEvents).hasSize(1);
        assertThat(failedEvents.get(0).getRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("여러 PENDING 이벤트를 한 번에 처리한다")
    void multiplePendingEvents_shouldBeProcessed() {
        // given
        outboxEventRepository.save(new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":10}"));
        outboxEventRepository.save(new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":11}"));
        outboxEventRepository.save(new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":12}"));

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when
        outboxRetryScheduler.retryFailedEvents();

        // then
        List<OutboxEvent> processedEvents = outboxEventRepository.findByStatus(OutboxStatus.PROCESSED);
        assertThat(processedEvents).hasSize(3);
    }
}
