package com.ecommerce.application.service;

import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.entity.OutboxStatus;
import com.ecommerce.domain.repository.OutboxEventRepository;
import com.ecommerce.infrastructure.external.DataPlatformService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventPublisher 테스트")
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private DataPlatformService dataPlatformService;

    @InjectMocks
    private OutboxEventPublisher outboxEventPublisher;

    @Test
    @DisplayName("PENDING 상태의 이벤트를 처리한다")
    void processPendingEvents() {
        // given
        OutboxEvent event1 = new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":1}");
        event1.setId(1L);
        OutboxEvent event2 = new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":2}");
        event2.setId(2L);

        when(outboxEventRepository.findByStatus(OutboxStatus.PENDING))
                .thenReturn(Arrays.asList(event1, event2));
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when
        outboxEventPublisher.processPendingEvents();

        // then
        assertThat(event1.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(event2.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
        verify(dataPlatformService, times(2)).sendOrderData(anyString());
    }

    @Test
    @DisplayName("전송 실패 시 FAILED 상태로 변경한다")
    void processPendingEvents_Failed() {
        // given
        OutboxEvent event = new OutboxEvent("ORDER_COMPLETED", "{\"orderId\":1}");
        event.setId(1L);

        when(outboxEventRepository.findByStatus(OutboxStatus.PENDING))
                .thenReturn(Arrays.asList(event));
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(false);

        // when
        outboxEventPublisher.processPendingEvents();

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(outboxEventRepository).save(event);
    }
}
