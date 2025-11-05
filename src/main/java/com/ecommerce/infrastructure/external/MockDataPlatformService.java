package com.ecommerce.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 데이터 플랫폼 Mock 구현
 * 실제 환경에서는 HTTP Client를 사용하여 외부 API 호출
 */
@Slf4j
@Service
public class MockDataPlatformService implements DataPlatformService {

    @Override
    public boolean sendOrderData(String orderData) {
        try {
            // 실제 환경에서는 여기서 HTTP 요청을 보냄
            log.info("외부 데이터 플랫폼으로 주문 데이터 전송: {}", orderData);

            // Mock: 항상 성공
            return true;
        } catch (Exception e) {
            log.error("데이터 플랫폼 전송 실패", e);
            return false;
        }
    }
}
