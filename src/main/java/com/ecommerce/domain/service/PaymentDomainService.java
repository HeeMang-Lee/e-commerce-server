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
 * - 포인트/쿠폰 차감은 상위 Facade에서 처리
 * - 부가 로직(데이터 플랫폼 전송, 랭킹 기록)은 이벤트로 분리
 * - 다른 도메인 서비스에 의존하지 않음
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
