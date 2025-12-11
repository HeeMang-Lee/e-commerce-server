# MSA 전환 시 트랜잭션 처리 방안 설계

## 들어가며

현재 이커머스 서비스는 모놀리식 아키텍처로 운영 중이다. 주문, 결제, 재고, 쿠폰, 포인트가 하나의 애플리케이션에서 돌아간다. 만약 트래픽이 증가해서 도메인별로 서비스를 분리해야 한다면, 지금처럼 하나의 트랜잭션으로 묶을 수 없게 된다.

이 문서는 **분산 환경에서 발생하는 트랜잭션 문제와 한계**를 분석하고, 이를 해결하기 위한 방안을 설계한다.

---

## 1. 현재 아키텍처

### 모놀리식 구조

```
┌─────────────────────────────────────────────────────────────┐
│                    E-commerce Server                         │
├─────────────────────────────────────────────────────────────┤
│  OrderService (Facade)                                       │
│    ├── ProductDomainService (재고 차감)                      │
│    ├── PointDomainService (포인트 차감)                      │
│    ├── CouponDomainService (쿠폰 사용)                       │
│    └── PaymentDomainService (결제 완료)                      │
├─────────────────────────────────────────────────────────────┤
│                      MySQL (단일 DB)                         │
│  users | products | orders | order_items | order_payments   │
│  coupons | user_coupons | point_histories | outbox_events   │
└─────────────────────────────────────────────────────────────┘
```

### 현재 트랜잭션 처리

```java
private PaymentResponse executePayment(Long orderId, PaymentRequest request) {
    try {
        pointDomainService.deductPoint(...);      // 각자 @Transactional
        couponDomainService.useCoupon(...);       // 각자 @Transactional
        paymentDomainService.completePayment(...); // 각자 @Transactional

        publishPaymentCompletedEvent(order, payment);
        return response;

    } catch (Exception e) {
        executeCompensationTransaction(...);  // 수동 롤백
        throw e;
    }
}
```

현재는 각 도메인 서비스가 독립적으로 `@Transactional`을 관리하고, 실패 시 보상 트랜잭션으로 수동 롤백한다. **같은 DB를 쓰기 때문에** 보상 로직이 비교적 단순하다.

---

## 2. 분산 환경 전환 시 발생하는 문제

### 도메인별 서비스/DB 분리

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           API Gateway                                     │
└─────────────┬─────────────┬─────────────┬─────────────┬─────────────────┘
              │             │             │             │
      ┌───────▼───────┐ ┌───▼───┐ ┌──────▼──────┐ ┌────▼────┐
      │ Order Service │ │Product│ │Point Service│ │ Coupon  │
      │               │ │Service│ │             │ │ Service │
      └───────┬───────┘ └───┬───┘ └──────┬──────┘ └────┬────┘
              │             │            │             │
      ┌───────▼───────┐ ┌───▼───┐ ┌──────▼──────┐ ┌────▼────┐
      │   Order DB    │ │Product│ │  Point DB   │ │ Coupon  │
      │               │ │  DB   │ │             │ │   DB    │
      └───────────────┘ └───────┘ └─────────────┘ └─────────┘
```

### 문제 1: 원자성(Atomicity) 보장 불가

```
주문 결제 요청
    │
    ├── [1] Product Service: 재고 차감 ✅ (Product DB 커밋)
    │
    ├── [2] Point Service: 포인트 차감 ✅ (Point DB 커밋)
    │
    ├── [3] Coupon Service: 쿠폰 사용 ❌ (실패!)
    │
    └── Product DB, Point DB는 이미 커밋됨. 롤백 불가능.
```

**단일 DB에서는** `@Transactional`이 실패 시 자동으로 롤백해줬다. **DB가 분리되면** 각 서비스의 트랜잭션이 독립적이라 한 서비스의 롤백이 다른 서비스에 전파되지 않는다.

### 문제 2: 격리성(Isolation) 상실

```
시간  Order Service          Product Service         다른 사용자
─────────────────────────────────────────────────────────────
T1    주문 요청
T2                          재고 10 → 9 (커밋)
T3                                                  재고 조회: 9개
T4    포인트 차감 실패
T5                          재고 복구 9 → 10
T6                                                  "아까 9개였는데?"
```

중간 상태가 외부에 노출된다. 다른 사용자가 "재고 9개"를 보고 주문했는데, 실제로는 복구되어 10개가 되는 상황이 발생할 수 있다.

### 문제 3: 네트워크 불확실성

```
Order Service ──HTTP 요청──▶ Point Service
                   │
                   ├── 요청 전송 성공, 응답 타임아웃
                   │      → Point Service는 처리했나? 안 했나?
                   │
                   └── 요청 전송 실패
                          → 재시도하면 중복 처리될 수 있음
```

네트워크는 언제든 실패할 수 있다. "요청은 갔는데 응답이 안 온" 상황에서 재시도하면 중복 처리될 위험이 있다.

### 문제 4: 부분 실패 복구의 복잡성

```java
// 모놀리식에서의 보상 트랜잭션 (현재)
private void executeCompensationTransaction(...) {
    restoreStockWithLock(item.getProductId(), item.getQuantity());  // 같은 DB
    pointDomainService.chargePoint(order.getUserId(), usedPoint);   // 같은 DB
    couponDomainService.cancelCouponUsage(userCouponId);           // 같은 DB
}
```

현재는 같은 DB라서 보상 로직도 비교적 단순하다. 분산 환경에서는:

```
보상 트랜잭션 실행
    │
    ├── Product Service 호출: 재고 복구 ✅
    │
    ├── Point Service 호출: 포인트 복구 ❌ (네트워크 오류)
    │
    └── 보상 트랜잭션마저 실패. 데이터 정합성 깨짐.
```

**보상 트랜잭션도 실패할 수 있다**는 게 분산 환경의 어려움이다.

---

## 3. 해결 방안 비교

### 방안 1: 2PC (Two-Phase Commit)

분산 트랜잭션의 "정석"이지만, 실무에서 잘 안 쓰인다.

```
[Coordinator]
     │
     ├── Phase 1: Prepare
     │      ├── Product Service: "커밋 가능?" → "OK"
     │      ├── Point Service: "커밋 가능?" → "OK"
     │      └── Coupon Service: "커밋 가능?" → "OK"
     │
     └── Phase 2: Commit
            ├── Product Service: "커밋해" → Done
            ├── Point Service: "커밋해" → Done
            └── Coupon Service: "커밋해" → Done
```

**한계:**
- **성능**: 2단계로 진행되어 지연 발생
- **가용성**: 코디네이터 장애 시 전체 블로킹
- **확장성**: 참여자가 늘어날수록 코디네이터 부하 증가
- **복잡도**: 타임아웃, 네트워크 파티션 처리 어려움

### 방안 2: Saga 패턴 - Orchestration

중앙 오케스트레이터가 전체 플로우를 제어한다.

```
┌────────────────────────────────────────────────────────┐
│                    Orchestrator                         │
│                                                        │
│  1. Product Service 호출 → 재고 차감                   │
│  2. Point Service 호출 → 포인트 차감                   │
│  3. Coupon Service 호출 → 쿠폰 사용                    │
│  4. 실패 시 역순으로 보상 트랜잭션 호출                 │
│                                                        │
└────────────────────────────────────────────────────────┘
```

**장점:**
- 플로우 가시성 확보 (한 곳에서 전체 흐름 파악)
- 디버깅/추적 용이
- 트랜잭션 상태를 한 곳에서 관리해서 "지금 이 거래가 어느 단계인지" 명확하게 파악 가능
- 특정 한도나 제약 조건을 중앙에서 체크하기 용이

**한계:**
- SPOF (Single Point of Failure): 오케스트레이터 죽으면 전체 마비
- 오케스트레이터에 부하 집중
- 서비스 간 결합도 증가 (오케스트레이터가 모든 서비스를 알아야 함)

**Orchestration이 적합한 경우:**
- 거래 한도나 리밋 체크가 필요한 금융 도메인 (외화 송금 한도 등)
- 전체 트랜잭션 상태를 실시간으로 추적해야 하는 경우
- 복잡한 비즈니스 규칙이 트랜잭션 중간 단계에서 적용되어야 하는 경우

### 방안 3: Saga 패턴 - Choreography

각 서비스가 이벤트를 발행하고 구독하며 자율적으로 동작한다.

```
OrderCreatedEvent
       │
       ▼
┌─────────────┐     StockDeductedEvent     ┌─────────────┐
│   Product   │ ─────────────────────────▶ │    Point    │
│   Service   │                            │   Service   │
└─────────────┘                            └──────┬──────┘
                                                  │
                                    PointDeductedEvent
                                                  │
                                                  ▼
                                           ┌─────────────┐
                                           │   Coupon    │
                                           │   Service   │
                                           └─────────────┘
```

**장점:**
- SPOF 없음 (중앙 조정자 없음)
- 서비스 간 느슨한 결합
- 개별 서비스 확장 용이

**한계:**
- 전체 플로우 파악 어려움 (이벤트가 여기저기 흩어짐)
- 순환 의존성 발생 가능
- 디버깅/추적 복잡

---

## 4. 어떤 방식을 선택할 것인가?

### 도메인 특성에 따른 선택

Orchestration과 Choreography 중 무엇이 좋다고 단정할 수 없다. **도메인 요구사항에 따라 선택**해야 한다.

**Orchestration이 적합한 경우:**
- 거래 한도/리밋 체크가 필요한 금융 도메인
- "이 거래가 지금 어느 단계인지" 실시간 추적이 중요한 경우
- 복잡한 비즈니스 규칙이 트랜잭션 중간에 적용되어야 하는 경우

**Choreography가 적합한 경우:**
- 서비스 간 독립성이 중요한 경우
- 중앙 조정자 없이 확장성을 확보해야 하는 경우
- 각 서비스가 자율적으로 동작해야 하는 경우

### 이커머스에서의 선택: Choreography

이커머스 결제 플로우는 상대적으로 단순하다. 재고 → 포인트 → 쿠폰 → 결제 완료. 중간에 복잡한 한도 체크나 상태 분기가 없다.

Orchestration의 SPOF 문제(오케스트레이터가 죽으면 전체 마비)보다, Choreography의 "플로우 파악 어려움"이 운영 도구로 보완하기 더 쉽다고 판단했다.

### Choreography의 단점 보완

**1. 이벤트 네이밍 컨벤션**

```java
// {Domain}.{Entity}.{Action} 규칙
"order.order.created"
"product.stock.deducted"
"point.balance.charged"
```

**2. 분산 추적 (Distributed Tracing)**

모든 이벤트에 `traceId`를 포함시켜 Zipkin 같은 도구로 전체 플로우를 추적한다.

---

## 5. 결제 플로우 설계

### 정상 플로우

```
┌─────────────┐  order.created   ┌─────────────┐
│   Order     │ ───────────────▶ │   Product   │
│   Service   │                  │   Service   │
└─────────────┘                  └──────┬──────┘
                                        │ stock.deducted
                                        ▼
                                 ┌─────────────┐
                                 │    Point    │
                                 │   Service   │
                                 └──────┬──────┘
                                        │ point.deducted
                                        ▼
                                 ┌─────────────┐
                                 │   Coupon    │
                                 │   Service   │
                                 └──────┬──────┘
                                        │ coupon.used
                                        ▼
                                 ┌─────────────┐
                                 │   Order     │
                                 │ (결제 완료)  │
                                 └─────────────┘
```

### 실패 시 보상 플로우

```
쿠폰 사용 실패
       │
       ├──▶ Point Service: 포인트 복구
       │
       └──▶ Product Service: 재고 복구
                    │
                    └──▶ Order Service: 주문 취소
```

---

## 6. 데이터 정합성 보장 전략

### "항상 출금부터" 원칙

돈이나 재고처럼 **되돌리기 어려운 자원**을 다룰 때는 순서가 중요하다.

```
❌ 잘못된 순서 (입금 먼저)
1. 상대방 계좌에 입금 ✅
2. 내 계좌에서 출금 ❌ (잔액 부족!)
   → 이미 입금된 돈을 어떻게 회수하지?

✅ 올바른 순서 (출금 먼저)
1. 내 계좌에서 출금 ❌ (잔액 부족!)
   → 여기서 실패하면 끝. 상대방 계좌는 건드리지 않음.

1. 내 계좌에서 출금 ✅
2. 상대방 계좌에 입금 ❌ (시스템 오류)
   → 출금만 복구하면 됨 (내 시스템 안에서 해결 가능)
```

이커머스에서는 **재고 차감 → 포인트 차감 → 결제 완료** 순서로 처리한다. 재고를 먼저 확보해야 품절 상황에서 포인트만 빠지는 문제를 방지할 수 있다.

### 동기/비동기 통신 선택

**핵심 플로우는 동기(HTTP), 보상은 비동기(Kafka)**가 실용적이다.

```
핵심 플로우: HTTP (동기)
    ├── 재고 차감 요청 → 즉시 응답
    ├── 포인트 차감 요청 → 즉시 응답
    └── 결제 완료

보상 플로우: Kafka (비동기)
    ├── 재고 복구 이벤트 (실패해도 재시도)
    └── 포인트 복구 이벤트 (실패해도 재시도)
```

핵심 플로우는 동기로 빠른 피드백을 받고, 보상은 비동기로 안정성을 확보한다.

### Transactional Outbox 패턴

이벤트 발행 시점에 서버가 죽으면 이벤트가 유실된다. **비즈니스 로직과 이벤트 저장을 같은 트랜잭션**으로 묶어서 해결한다.

```
┌─────────────┐     ┌─────────────┐     ┌─────────┐
│  Service    │────▶│ Outbox 테이블│────▶│  Kafka  │
│ (트랜잭션)   │     │ (같은 DB)    │     │         │
└─────────────┘     └──────┬──────┘     └─────────┘
                           │
                    ┌──────▼──────┐
                    │ Outbox Relay│
                    │ (폴링/발행) │
                    └─────────────┘
```

### 멱등성(Idempotency) 보장

네트워크 문제로 이벤트가 중복 수신될 수 있다. `eventId`로 이미 처리한 이벤트인지 체크하고, 중복이면 무시한다.

### 재시도와 장애 대응

- **즉시 재시도**: 지수 백오프로 3회 재시도
- **즉시 재시도 실패**: DLQ(Dead Letter Queue)에 적재
- **배치 재처리**: 30분마다 DLQ에서 꺼내서 재시도
- **최종 실패**: 알람 발생, 수동 처리

---

## 7. 모니터링과 운영

### 트랜잭션 상태 추적

분산 트랜잭션에서 "이 거래가 지금 어느 단계인지" 파악하는 게 중요하다.

```sql
CREATE TABLE saga_state_log (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    current_step VARCHAR(50) NOT NULL,  -- STOCK_DEDUCTING, POINT_DEDUCTING, ...
    status VARCHAR(20) NOT NULL,        -- IN_PROGRESS, COMPLETED, FAILED
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

이 테이블로:
- "5분 이상 멈춘 거래" 조회 가능
- 장애 시 어느 단계에서 문제가 생겼는지 파악
- 수동 보상 처리 시 어디까지 진행됐는지 확인

### 멈춘 트랜잭션 탐지

```java
@Scheduled(fixedDelay = 60000)
public void detectStuckTransactions() {
    LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
    List<SagaStateLog> stuck = repository.findByStatusAndUpdatedAtBefore("IN_PROGRESS", threshold);

    for (SagaStateLog saga : stuck) {
        alertService.notify("트랜잭션 정체: " + saga.getTraceId());
    }
}
```

### 필수 모니터링 지표

- **Saga 완료율**: < 99% 시 알람
- **평균 처리 시간**: > 5초 시 알람
- **멈춘 트랜잭션 수**: > 0 시 알람
- **DLQ 적재량**: > 100 시 알람

---

## 8. 단계적 전환 전략

빅뱅 전환은 위험하다. 점진적으로 전환한다.

### Phase 1: 이벤트 기반 구조 도입 (현재 완료)

```
┌─────────────────────────────────────────┐
│         Monolith (현재)                  │
│  OrderService → EventPublisher          │
│       └── RankingEventHandler           │
│       └── DataPlatformEventHandler      │
└─────────────────────────────────────────┘
```

✅ 완료 항목:
- 결제 완료 이벤트 발행
- 이벤트 핸들러 분리 (Self-Invocation 해결)
- Outbox 패턴 적용
- 배치 스케줄러로 이벤트 유실 복구

### Phase 2: 메시지 브로커 도입

```
┌─────────────────┐     ┌─────────┐     ┌─────────────────┐
│    Monolith     │────▶│  Kafka  │────▶│ Consumer Worker │
│ (이벤트 발행)    │     │         │     │ (별도 프로세스)  │
└─────────────────┘     └─────────┘     └─────────────────┘
```

- `ApplicationEventPublisher` → Kafka Producer로 전환
- Consumer를 별도 프로세스로 분리
- 이벤트 스키마 표준화

### Phase 3: 도메인 서비스 분리

```
┌─────────────┐     ┌─────────┐     ┌─────────────┐
│Order Service│────▶│  Kafka  │────▶│Point Service│
│             │     │         │     │ (별도 서비스)│
└─────────────┘     └─────────┘     └─────────────┘
```

- 트래픽이 많거나 독립적 확장이 필요한 도메인부터 분리
- 우선 분리 대상: 쿠폰 서비스 (선착순 이벤트 트래픽 집중)

### Phase 4: 완전한 MSA

```
Order ←→ Product ←→ Point ←→ Coupon
   └───────────┬───────────────┘
               │
            Kafka
```

---

## 9. 한계와 트레이드오프

### Saga 패턴의 본질적 한계

**격리성(Isolation) 부재:**
- 중간 상태가 외부에 노출된다
- 재고 차감 후 결제 전에 다른 사용자가 조회하면 차감된 재고가 보인다
- 해결: Semantic Lock ("처리 중" 상태 표시) 또는 재고 예약 패턴

**최종 일관성(Eventual Consistency):**
- 즉각적인 일관성 보장 불가
- "몇 초 내에 일관성이 맞춰진다"는 전제 필요

### 결론

분산 환경으로 전환하면 **단일 트랜잭션의 편리함을 잃는다**. 대신 **확장성과 독립 배포**를 얻는다.

현재 규모에서는:
- ✅ 이벤트 기반 구조 도입 (부가 로직 분리)
- ✅ 보상 트랜잭션 패턴 학습
- ⏳ 메시지 브로커 도입 → 트래픽 증가 시
- ⏳ 서비스 분리 → 특정 도메인 부하 집중 시

**모놀리식으로 시작하되 분리하기 용이하게 아키텍처를 잡아두고, 트래픽이 정당화할 때 분리**하는 게 현실적인 접근이다.

---

## References

- [마이크로서비스 패턴 - Saga](https://microservices.io/patterns/data/saga.html)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Spring Cloud Data Flow](https://spring.io/projects/spring-cloud-dataflow)
- [Distributed Tracing with Zipkin](https://zipkin.io/)
- [토스ㅣSLASH 24 - 보상 트랜잭션으로 분산 환경에서도 안전하게 환전하기](https://www.youtube.com/watch?v=xpwRTu47fqY&t=937s)
