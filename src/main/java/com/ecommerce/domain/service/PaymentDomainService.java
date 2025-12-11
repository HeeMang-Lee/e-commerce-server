package com.ecommerce.domain.service;

import com.ecommerce.domain.entity.OrderPayment;
import com.ecommerce.domain.entity.PaymentStatus;
import com.ecommerce.domain.repository.OrderPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 도메인 서비스
 *
 * 책임:
 * - 결제 완료/실패 처리
 * - 트랜잭션 경계 관리
 *
 * 주의:
 * - 이벤트 발행은 상위 Facade(OrderService)에서 처리
 * - 도메인 서비스는 순수하게 도메인 로직만 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentDomainService {

    private final OrderPaymentRepository orderPaymentRepository;

    @Transactional
    public OrderPayment completePayment(Long orderId) {
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제입니다");
        }

        payment.complete();
        orderPaymentRepository.save(payment);

        return payment;
    }

    @Transactional
    public OrderPayment failPayment(Long orderId) {
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            log.warn("이미 완료된 결제를 실패로 변경할 수 없습니다: orderId={}", orderId);
            return payment;
        }

        payment.fail();
        orderPaymentRepository.save(payment);
        log.info("결제 상태 FAILED로 변경: orderId={}", orderId);

        return payment;
    }
}
