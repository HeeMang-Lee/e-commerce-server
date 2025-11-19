package com.ecommerce.infrastructure.external;

/**
 * 외부 데이터 플랫폼 연동 서비스
 */
public interface DataPlatformService {

    /**
     * 주문 데이터를 외부 플랫폼으로 전송합니다.
     */
    boolean sendOrderData(String orderData);
}
