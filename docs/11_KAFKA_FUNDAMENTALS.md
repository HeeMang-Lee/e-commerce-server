# Kafka 기초 개념 학습

## 들어가며

현재 이커머스 서비스에서 결제 완료 후 랭킹 업데이트는 Spring Event로 처리하고 있다.

```java
eventPublisher.publish(paymentCompletedEvent);
```

같은 JVM 안에서 동기/비동기로 이벤트를 처리하는 방식이다. 근데 서비스가 커지면 어떻게 될까?

- 서비스가 여러 인스턴스로 분리되면 Spring Event는 JVM 경계를 넘지 못한다
- 트래픽이 폭증하면 처리 속도를 따라가지 못한다
- 장애 발생 시 이벤트가 유실된다

Kafka는 이런 문제를 해결하기 위한 분산 메시지 시스템이다.

### Kafka 도입의 핵심 관점: 관심사의 분리

Kafka를 "비동기 처리로 성능 개선"하는 도구로만 보기 쉽다. 하지만 진짜 핵심 가치는 **관심사의 분리**다.

**AS-IS: 회원 서버가 모든 것을 알아야 함**
```java
class SignUpService {
    void complete() {
        couponServer.issueCoupon();      // 쿠폰 서버 알아야 함
        accountServer.createAccount();    // 계좌 서버 알아야 함
        rewardServer.giveWelcomeBonus(); // 리워드 서버 알아야 함
        // ... 연동하는 서버가 늘어날수록 회원 서버가 복잡해짐
    }
}
```

**TO-BE: 이벤트 발행만 하면 끝**
```java
class SignUpService {
    void complete() {
        // 회원 서버는 "가입 완료" 이벤트만 발행
        kafkaTemplate.send("user-signup-complete", event);
        // 누가 처리하는지 관심 없음
    }
}

// 각 서버가 독립적으로 이벤트 구독
@KafkaListener(topics = "user-signup-complete")
void handleSignUp(SignUpEvent event) { /* 쿠폰 발급 */ }

@KafkaListener(topics = "user-signup-complete")
void handleSignUp(SignUpEvent event) { /* 계좌 생성 */ }
```

회원 서버는 다른 서버를 몰라도 된다. 새로운 서버 추가해도 회원 서버 수정이 필요 없다.

---

## 1. Kafka 핵심 컴포넌트

### Broker

메시지를 저장하고 전달하는 서버다.

```
┌─────────────────────────────────────────┐
│              Kafka Broker               │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
│  │ Topic A │  │ Topic B │  │ Topic C │ │
│  └─────────┘  └─────────┘  └─────────┘ │
└─────────────────────────────────────────┘
```

**실무에서는 하나의 브로커 클러스터에 여러 토픽을 운영한다.** 토픽이 100개 넘는 경우도 흔하다. 토픽마다 브로커를 새로 띄우면 유지보수, 버전업 대응 등 운영 부담이 커진다.

DB 하나에 여러 스키마(테이블)를 두는 것과 비슷한 개념이다.

**브로커 수 결정 기준**

최소 3대로 시작한다. Replication Factor(RF) = 3이 기준이므로 2대가 죽어도 데이터가 유지된다.

```
필요 브로커 수 = (목표 TPS ÷ 브로커당 처리량) × 1.2~1.3

예시:
- 목표: 초당 50만 메시지
- 브로커 1대당 처리량: 초당 10만 메시지
- 필요 브로커: 50만 ÷ 10만 = 5대
- 여유 포함: 5 × 1.3 = 6~7대
```

20~30% 여유를 두는 이유:
- 브로커 1대 장애 시 나머지가 부하를 감당해야 함
- 트래픽 급증(이벤트, 프로모션) 대응

### Topic

메시지의 카테고리다. DB의 테이블이라고 생각하면 쉽다.

```
payment-completed  ← 결제 완료 이벤트
order-created      ← 주문 생성 이벤트
coupon-issued      ← 쿠폰 발급 이벤트
```

Producer는 특정 Topic에 메시지를 발행하고, Consumer는 구독한 Topic에서 메시지를 가져간다.

### Partition

Topic 내의 물리적 분할 단위다. **Kafka의 병렬 처리 핵심**이다.

```
Topic: payment-completed
┌─────────────────────────────────────────────────────────┐
│  Partition 0: [msg1] [msg4] [msg7] [msg10] ...         │
│  Partition 1: [msg2] [msg5] [msg8] [msg11] ...         │
│  Partition 2: [msg3] [msg6] [msg9] [msg12] ...         │
└─────────────────────────────────────────────────────────┘
```

왜 나눌까?

1. **병렬 처리**: 파티션 수 = 최대 동시 처리 가능한 Consumer 수
2. **순서 보장**: 같은 파티션 내에서만 순서가 보장된다
3. **확장성**: 파티션을 늘리면 처리량을 늘릴 수 있다

### Producer

메시지를 발행하는 주체다.

```java
// Spring Kafka 예시
kafkaTemplate.send("payment-completed", orderId, paymentEvent);
//                  Topic              Key     Value
```

Key 값에 따라 어느 파티션으로 갈지 결정된다. 같은 Key는 항상 같은 파티션으로 간다.

### Consumer

메시지를 소비하는 주체다.

```java
@KafkaListener(topics = "payment-completed", groupId = "ranking-service")
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    rankingService.incrementSalesCount(event.getProductId());
}
```

### Producer와 Consumer의 관계

**서로 모르는 독립적인 존재다.**

```
프로듀서: 책상 위에 휴지를 올려놓는 사람
컨슈머: 휴지가 있구나 하고 가져다 쓰는 사람
→ 서로 누군지 모름
```

- 프로듀서는 메시지를 토픽에 발행만 함. 누가 가져가는지 관심 없음
- 컨슈머는 누가 메시지를 보냈는지 모름. 그냥 메시지가 있으면 가져가서 처리

컨슈머는 주기적으로 새 메시지가 있는지 확인한다(Polling). while(true) 루프를 돌며 설정에 따라 몇 초 단위로 계속 체크하는 구조다.

### Consumer Group

Consumer들의 논리적 그룹이다. **메시지를 처리하는 하나의 집합 = 보통 하나의 서버/서비스**라고 생각하면 된다.

```
Topic: payment-completed (3 partitions)

Consumer Group: ranking-service
┌─────────────────────────────────────────────────┐
│  Consumer 1 ← Partition 0                       │
│  Consumer 2 ← Partition 1                       │
│  Consumer 3 ← Partition 2                       │
└─────────────────────────────────────────────────┘

Consumer Group: notification-service
┌─────────────────────────────────────────────────┐
│  Consumer A ← Partition 0, 1                    │
│  Consumer B ← Partition 2                       │
└─────────────────────────────────────────────────┘
```

핵심 규칙:
- **하나의 파티션은 그룹 내 하나의 Consumer만 읽을 수 있다**
- 다른 Consumer Group은 같은 메시지를 독립적으로 읽는다
- Consumer가 파티션보다 많으면 놀고 있는 Consumer가 생긴다
- 그룹 내에서는 메시지가 딱 1번만 처리됨

**그룹을 늘려서 성능을 높일 수 있나요?**

아니다. 그룹을 늘리면 같은 메시지가 중복 처리된다. 성능 향상이 아니라 중복 처리 문제가 발생한다. 성능을 높이려면 **파티션 + 컨슈머**를 함께 증가시켜야 한다.

### Offset

파티션 내 메시지의 순차적 ID다. "어디까지 읽었는지" 추적하는 데 사용된다.

```
Partition 0: [0] [1] [2] [3] [4] [5] [6] [7] ...
                          ↑
                   Current Offset = 3
                   (다음에 읽을 메시지)
```

Consumer Group별로 Offset을 관리한다. 그래서 ranking-service가 5번까지 읽어도, notification-service는 3번부터 읽을 수 있다.

커밋된 오프셋은 Kafka 내부 토픽(`__consumer_offsets`)에 저장되어 컨슈머 그룹 차원에서 공유된다.

### Replication

Broker 장애에 대비한 복제 메커니즘이다.

```
Broker 1 (Leader)     Broker 2 (Follower)   Broker 3 (Follower)
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│ Partition 0     │   │ Partition 0     │   │ Partition 0     │
│ [0][1][2][3]    │──▶│ [0][1][2][3]    │──▶│ [0][1][2][3]    │
└─────────────────┘   └─────────────────┘   └─────────────────┘
      Write                 Sync                  Sync
```

- **Leader**: 읽기/쓰기를 담당하는 메인 파티션
- **Follower**: Leader를 복제하는 백업
- **Replication Factor = 3**: Leader 1 + Follower 2 (실무 기준)

Leader가 죽으면 Follower 중 하나가 새 Leader가 된다.

**리더 장애 시 지연 시간이 있나요?**

당연히 있다. 리더 감지 → 팔로워 선출 → 승격 완료까지 시간이 소요된다. 그 동안 해당 파티션 처리가 지연된다. Kafka는 비동기 처리가 전제이므로 "언제 될지 모르지만 언젠가 처리됨"이 기본 가정이다.

**한 브로커에 리더가 2개여도 되나요?**

된다. 파티션별 리더이므로 문제없다. "브로커당 리더 1개"가 아니라 "파티션당 리더 1개"가 규칙이다.

```
broker1 장애 발생
    ↓
partition0의 리더 죽음
    ↓
broker2 또는 broker3의 follower 중 하나가 리더로 승격
    ↓
broker3에 partition1 리더 + partition0 리더 = 2개 리더
    ↓
파티션별 리더이므로 OK!
```

### Cluster

여러 Broker의 집합이다. 브로커가 죽으면 안 되니까 보통 3~5대로 클러스터를 구성한다. 1~2대 죽어도 서비스 운영이 가능하도록.

```
┌─────────────────────────────────────────────────────────┐
│                     Kafka Cluster                        │
│                                                          │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐          │
│  │ Broker 1 │    │ Broker 2 │    │ Broker 3 │          │
│  │ P0(L)    │    │ P0(F)    │    │ P1(L)    │          │
│  │ P1(F)    │    │ P2(L)    │    │ P2(F)    │          │
│  └──────────┘    └──────────┘    └──────────┘          │
│                                                          │
│  ┌─────────────────────────────────────────────────┐    │
│  │              ZooKeeper / KRaft                   │    │
│  │  (클러스터 메타데이터, 리더 선출 관리)            │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘

L = Leader, F = Follower
```

---

## 2. 파티션 수 결정 (가장 중요!)

### 핵심: 파티션은 늘리기만 가능, 줄일 수 없음

**왜 줄일 수 없나?**

파티션에는 이미 메시지가 저장되어 있다. 파티션을 삭제하면 그 안의 메시지도 사라진다.

```
파티션 0: [msg0, msg3, msg6, msg9, ...]
파티션 1: [msg1, msg4, msg7, msg10, ...]
파티션 2: [msg2, msg5, msg8, msg11, ...]  ← 아직 처리 안 된 메시지 있음

파티션 2를 없애면?
→ msg2, msg5, msg8, msg11... 전부 유실!
→ 컨슈머 오프셋 정보도 꼬임
```

Kafka는 메시지 유실을 허용하지 않는 설계 철학을 가지고 있어서, 파티션 축소 자체를 지원하지 않는다.

**파티션 늘리기도 주의가 필요하다**

파티션을 늘리면 **기존 키의 파티션 매핑이 깨진다.**

```
Before: 파티션 3개
hash(orderId) % 3 = 파티션 번호

orderId=100 → hash % 3 = 1 → 파티션 1
orderId=200 → hash % 3 = 2 → 파티션 2

After: 파티션 6개로 증가
hash(orderId) % 6 = 파티션 번호

orderId=100 → hash % 6 = 4 → 파티션 4 (변경됨!)
orderId=200 → hash % 6 = 2 → 파티션 2 (유지)
```

같은 키가 다른 파티션으로 가면서 **순서 보장이 깨진다.**

```
파티션 1: [주문생성(orderId=100)] [결제요청(orderId=100)]
    ↓
파티션 증가 후
    ↓
파티션 4: [결제완료(orderId=100)]  ← 다른 파티션으로 감

→ 주문생성 → 결제요청 → 결제완료 순서가 보장 안 됨!
```

**그래서 파티션 변경은 신중해야 한다:**
- 줄이기: 불가능 (토픽 재생성 필요)
- 늘리기: 가능하지만 순서 보장이 깨질 수 있음
- 결론: **초기에 보수적으로 설정하고, 늘리더라도 영향도 분석 필수**

### 파티션 수 계산

```
파티션 수 = 목표 처리량 ÷ 파티션당 처리량

예시:
- 목표: 초당 1만 건
- 파티션당 처리량: 초당 1천 건
- 필요 파티션: 10개
```

파티션당 처리량은 상황에 따라 다르다:
- **낮아지는 경우**: DB 쿼리 포함, 외부 API 호출, 복잡한 비즈니스 로직
- **높아지는 경우**: 단순 로깅, 메모리 내 처리

### 파티션 수 결정 전략

```
1. 초기값 3개로 시작 (보수적)
       ↓
2. 성능이 느리다?
   → 컨슈머 로직 개선 (쿼리 최적화 등)
       ↓
3. 그래도 답이 없다?
   → 파티션 수 증가 (최종 선택)
       ↓
4. 파티션 늘리면 컨슈머도 함께 증가
```

### 토픽 특성별 설정

**순서 보장 중요 (결제, 재고 차감)**
- 파티션 적게: 3~6개
- 같은 키의 메시지는 같은 파티션으로 → 순서 보장

**처리량 중요 (로깅, 알림)**
- 파티션 많이: 12~24개
- 순서보다 속도 우선

### 파티션과 컨슈머의 관계

**규칙: 파티션당 컨슈머 최대 1개**

```
파티션 4개 + 컨슈머 1개
→ 컨슈머 1개가 4개 파티션 순차 처리 (비효율)

파티션 4개 + 컨슈머 4개
→ 1:1 매핑, 동시 처리 성능 최대 (이상적)

파티션 4개 + 컨슈머 5개
→ 1개 컨슈머는 놀게 됨 (낭비)
```

**결론**
- 파티션 수 = 최대 동시 처리 성능
- 파티션 늘리면 컨슈머도 같이 늘려야 함

### 파티션 증가 시 주의사항: auto.offset.reset

```
파티션 3개 → 10개로 증가
    ↓
새로 추가된 파티션 7개는 오프셋 정보가 없음
    ↓
auto.offset.reset=earliest 설정 필요
    ↓
처음부터 메시지를 읽어감
```

설정하지 않으면 새 파티션의 메시지를 못 읽을 수 있다.

---

## 3. 메시지 흐름과 순서 보장

전체 흐름을 한눈에 보면:

```
┌──────────┐     ┌─────────────────────────────────┐     ┌───────────────────┐
│ Producer │────▶│        Kafka Cluster            │────▶│  Consumer Group   │
│          │     │                                  │     │                   │
│ Order    │     │  Topic: payment-completed        │     │  ┌─────────────┐ │
│ Service  │     │  ┌──────┐ ┌──────┐ ┌──────┐    │     │  │ Consumer 1  │ │
│          │     │  │ P0   │ │ P1   │ │ P2   │    │     │  │ ← P0        │ │
└──────────┘     │  └──────┘ └──────┘ └──────┘    │     │  ├─────────────┤ │
                 └─────────────────────────────────┘     │  │ Consumer 2  │ │
                                                          │  │ ← P1, P2    │ │
                                                          │  └─────────────┘ │
                                                          └───────────────────┘
```

### 순서 보장의 핵심

**Kafka는 파티션 단위로만 순서를 보장한다.**

```
Key = "order-123"일 때:

Partition 1: [주문생성] → [결제요청] → [결제완료] → [배송시작]
              ↑ 항상 이 순서 보장
```

만약 Key 없이 보내면? Round-Robin으로 파티션이 선택되어 순서가 뒤섞일 수 있다.

```
Partition 0: [주문생성] [결제완료]
Partition 1: [결제요청] [배송시작]

→ Consumer가 읽는 순서: 결제요청 → 주문생성 (순서 꼬임!)
```

### 파티션 Key 설계

| 시나리오 | 권장 Key | 이유 |
|---------|---------|------|
| 주문 이벤트 | orderId | 같은 주문의 이벤트 순서 보장 |
| 사용자 포인트 | userId | 같은 사용자의 포인트 연산 순서 보장 |
| 상품 재고 | productId | 같은 상품의 재고 연산 순서 보장 |
| 쿠폰 발급 | couponId | 같은 쿠폰의 발급 순서 보장 |

---

## 4. Consumer Group과 Rebalancing

### Rebalancing이란?

Consumer Group 내에서 파티션을 재분배하는 과정이다.

**언제 발생하나?**
- Consumer 추가/제거
- Consumer 장애 (heartbeat 실패)
- 파티션 수 변경
- Consumer가 너무 오래 처리 중

### Rebalancing 과정

```
Before: Consumer A(P0, P1), Consumer B(P2)

Consumer B 장애 발생!

Rebalancing...

After: Consumer A(P0, P1, P2)  ← P2 재할당
```

### 주의점

Rebalancing 동안 해당 Consumer Group의 **모든 Consumer가 일시 중지**된다.

```
┌─────────────────────────────────────────────────────────┐
│  Rebalancing 중...                                      │
│                                                          │
│  Consumer A: 처리 중지 ⏸                                │
│  Consumer B: 처리 중지 ⏸                                │
│  Consumer C: 처리 중지 ⏸                                │
│                                                          │
│  → 이 시간 동안 메시지 처리 지연 발생                    │
└─────────────────────────────────────────────────────────┘
```

대규모 트래픽 상황에서 자주 발생하면 문제가 된다.

### 대응 방안

1. **Consumer 수와 파티션 수 맞추기**: 파티션 3개면 Consumer도 3개
2. **session.timeout.ms 조정**: 장애 감지 시간 조절
3. **max.poll.records 조정**: 한 번에 가져오는 메시지 수 제한
4. **Sticky Assignor 사용**: Rebalancing 시 변경 최소화

---

## 5. Consumer Lag (컨슈머 렉)

### 컨슈머 렉이란?

```
프로듀서: 메시지 계속 발행 (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, ...)
컨슈머: 처리 속도가 느림 (0, 1, 2까지만 처리)
    ↓
처리 안 된 메시지가 계속 쌓임 = 컨슈머 렉
```

**문제**
- 렉이 계속 쌓이면 무한대로 증가
- 메시지 처리가 점점 지연됨

**렉 계산**
```
렉 = 프로듀서 발행 오프셋 - 컨슈머 처리 오프셋
```

**해결 순서**
```
1. 컨슈머 로직 최적화
2. 컨슈머 인스턴스 증가
3. 파티션 수 증가 (최후의 수단)
```

---

## 6. 오프셋 커밋 전략

### 자동 커밋의 실제 동작

```properties
enable.auto.commit=true
auto.commit.interval.ms=5000  # 기본값 5초
```

자동 커밋은 poll() 메서드 내부에서 비동기로 처리된다. 중요한 건 **커밋 순서**다.

```
poll() 호출
    ↓
1. 오프셋 자동 커밋 (이전 poll에서 가져온 메시지 기준)
    ↓
2. Fetch 요청
    ↓
3. 메시지 수신
    ↓
리스너에서 처리
```

커밋이 먼저 일어나고, 그 다음에 새 메시지를 가져온다. 이게 중복 처리의 원인이 된다.

### 자동 커밋의 문제 시나리오

```
poll() → 메시지 10개 수신
    ↓
리스너에서 3개 처리 중
    ↓
장애 발생 (아직 다음 poll 안 함 = 커밋 안 됨)
    ↓
재시작 → 이전 커밋 위치부터 시작
    ↓
10개 전부 다시 처리 (중복!)
```

핵심 문제: **처리 완료 시점 ≠ 커밋 시점**

### auto.commit.interval.ms 트레이드오프

| 설정 | 장점 | 단점 |
|------|------|------|
| 높은 값 (5초) | 네트워크 호출 감소 | 중복 처리 범위 증가 |
| 낮은 값 (1초) | 중복 처리 범위 감소 | 네트워크 부하 증가 |

**권장값: 1초**. 네트워크 부하보다 중복 처리 범위를 줄이는 게 더 중요하다.

### max.poll.records 설정

```properties
max.poll.records=500  # 기본값
```

poll() 한 번에 가져오는 최대 메시지 수다. 이 값이 크면:

```
500개 메시지 수신
    ↓
리스너에서 순차 처리 (오래 걸림)
    ↓
다음 poll() 호출 지연
    ↓
오프셋 커밋 지연
    ↓
장애 시 중복 처리 범위 증가
```

**권장값: 1**. I/O 작업이 포함된 현대 서비스에서는 1로 설정하는 게 안전하다. 성능 영향은 미미하다 (내부 버퍼에 데이터가 있으면 재Fetch 없이 바로 반환).

### 수동 커밋

자동 커밋의 문제를 해결하려면 수동 커밋을 쓴다.

```java
@KafkaListener(topics = "orders")
public void consume(ConsumerRecord<String, String> record,
                   Acknowledgment ack) {
    try {
        processOrder(record.value());
        ack.acknowledge();  // 처리 완료 후 커밋
    } catch (Exception e) {
        // 커밋하지 않음 → 재처리됨
    }
}
```

### Spring Kafka Ack Mode

| Mode | 동작 | 용도 |
|------|------|------|
| `RECORD` | 메시지 하나 처리할 때마다 커밋 | 가장 안전, 성능 낮음 |
| `BATCH` | poll()로 가져온 배치 단위로 커밋 | 성능과 안정성 균형 |
| `MANUAL` | acknowledge() 호출 시 커밋 | 가장 세밀한 제어 |
| `MANUAL_IMMEDIATE` | acknowledge() 호출 즉시 커밋 | 즉각적인 커밋 필요 시 |

### 권장 설정

```properties
# 자동 커밋 사용 시
enable.auto.commit=true
auto.commit.interval.ms=1000
max.poll.records=1

# 세션/하트비트 설정
session.timeout.ms=10000
heartbeat.interval.ms=3000
max.poll.interval.ms=300000

# Fetch 설정
fetch.min.bytes=1
fetch.max.bytes=5242880
fetch.max.wait.ms=500
```

하트비트는 session.timeout의 1/3 이하로 설정한다. 하트비트는 백그라운드 스레드로 전송되므로, 메인 스레드가 블로킹되어도 계속 전송된다.

---

## 7. 메시지 전달 보장과 멱등성

### At-Least-Once (Kafka 기본 보장)

```
메시지 유실 없음 ✓
중복 처리 가능성 있음 ⚠️
```

### 장애 시나리오별 결과

**처리 중 장애**
- 비즈니스 로직 수행 중 죽음 → 오프셋 미커밋 → 재시작 시 같은 메시지 다시 처리
- 결과: 데이터 유실 없음 ✓

**처리 완료 후, 커밋 전 장애**
- 비즈니스 로직 완료, 커밋 직전 죽음 → 재시작 시 같은 메시지 다시 처리
- 결과: 중복 처리 발생 ⚠️

### 중복 처리 대응: 멱등성 설계

```java
public void processOrder(OrderEvent event) {
    // 주문 ID로 이미 처리됐는지 체크
    if (orderRepository.existsById(event.getOrderId())) {
        log.info("이미 처리된 주문: {}", event.getOrderId());
        return;  // 중복 처리 방지
    }

    // 실제 처리
    orderRepository.save(event.toOrder());
}
```

멱등성 구현 방법:
- INSERT 전 ID 존재 여부 체크
- DB Unique 제약 조건
- 처리 완료 상태 저장 후 체크

---

## 8. 컨슈머 실패 메시지 처리 (DLT 전략)

### 실패 유형 구분이 먼저다

토스뱅크 환전 시스템에서는 실패를 두 가지로 구분한다.

**정상 실패 (비즈니스 실패)**
- 잔액 부족, 계좌 해지, 한도 초과
- 재시도해도 결과가 같다
- 처리: 그냥 실패 처리 또는 보상 트랜잭션

**비정상 실패 (시스템 실패)**
- 서버 에러, 타임아웃, 네트워크 장애
- 상대 서버가 회복되면 성공할 수 있다
- 처리: 재시도 전략 적용

### 비정상 실패 재시도 전략

```
1. 즉시 재시도 (3회)
      ↓ 실패
2. Kafka 메시지 스케줄러로 지연 재시도
   → 30초 후 → 1분 후 → 2분 후 → 점점 증가
      ↓ 실패
3. 배치로 재처리
   → 오케스트레이터가 상태 저장 → 중단된 지점부터 재시작
      ↓ 실패
4. Dead Letter 처리 + 알람
```

**왜 지연 시간을 늘리나?**

상대 서버가 회복할 시간을 줘야 한다. 1초마다 재시도하면 장애 상황에서 부하만 가중시킨다.

### Dead Letter 패턴 (토스뱅크)

**Consumer Dead Letter**
- 컨슈머 처리 실패 시 DL 서버가 받아서 재시도
- 일반적인 DLT 패턴

**Producer Dead Letter**
- 메시지 브로커 자체가 장애일 때
- 별도 브로커로 우회 발행
- 브로커 복구 후 원본 토픽으로 재발행

```
[Producer]
    │
    ├── 정상 → Kafka 발행
    │
    └── Kafka 장애 → 별도 DL 브로커 발행
                          │
                     [DL Server]
                          │
                     Kafka 복구 후 재발행
```

Producer 레벨 DL은 Kafka 장애에 대한 대비다. 메시지 유실을 막으려면 이 레벨까지 고려해야 한다.

### Spring Kafka 활용

```java
@KafkaListener(topics = "orders")
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2),
    dltStrategy = DltStrategy.FAIL_ON_ERROR
)
public void consume(OrderEvent event) {
    // 비즈니스 로직
}
```

### DLT 처리 실무 패턴

```
[DLT에 메시지 쌓임]
       ↓
[Slack 알림 발송] → 개발자 인지
       ↓
[원인 분석]
       ↓
┌──────────────────┐
│ 조치 선택        │
├──────────────────┤
│ 1. 원본 토픽 재발행 │
│ 2. 수동 재처리     │
│ 3. 영구 삭제      │
└──────────────────┘
```

### Orchestration vs Choreography에서의 실패 처리

Saga 패턴에 따라 실패 처리 방식이 다르다.

**Orchestration (토스뱅크 방식)**

오케스트레이터가 중앙에서 상태를 관리한다.

```
상태 로그 테이블 (INSERT only)
┌────────────────────────────────────────────┐
│ trace_id │ step        │ status  │ time   │
├────────────────────────────────────────────┤
│ tx-001   │ 출금요청     │ SUCCESS │ 10:00  │
│ tx-001   │ 입금요청     │ FAILED  │ 10:01  │
│ tx-001   │ 출금취소     │ SUCCESS │ 10:02  │
└────────────────────────────────────────────┘
```

- 오케스트레이터가 직접 상태 테이블 관리
- 중단된 지점부터 재시작 가능
- 재시도/보상 트랜잭션 흐름이 명확
- 단점: 오케스트레이터가 SPOF

**Choreography (이커머스 방식)**

각 서비스가 자율적으로 동작하고, 상태 추적은 별도 모니터링 서비스가 담당한다.

```
Product Service ──▶ StockDeductedEvent ──┐
                                          │
Point Service ──▶ PointDeductedEvent ────┼──▶ Saga Monitor Service
                                          │        │
Coupon Service ──▶ CouponUsedEvent ──────┘        ▼
                                            SagaStateLog DB
```

각 서비스의 책임:
- 자기 DLT 직접 관리
- 실패 시 보상 이벤트 발행 (예: `StockDeductFailedEvent`)
- 모든 이벤트에 `traceId` 포함

모니터링 서비스의 책임:
- 이벤트 수집해서 상태 테이블 업데이트
- 멈춘 트랜잭션 탐지 (5분 이상 IN_PROGRESS면 알람)
- Zipkin 같은 분산 추적 도구와 연동

**Choreography에서 "중단된 지점부터 재시작"이 가능한가?**

어렵다. 중앙 조정자가 없어서 "어디까지 진행됐는지" 파악이 복잡하다. 대신:
- 각 서비스가 독립적으로 재시도
- 최종 실패 시 보상 이벤트 체인으로 롤백
- 모니터링 서비스가 "짝이 안 맞는 트랜잭션" 탐지 후 수동 처리

**선택 기준**

| 요구사항 | 선택 |
|---------|------|
| 거래 한도/리밋 체크 필요 | Orchestration |
| 실시간 상태 추적 필요 | Orchestration |
| 서비스 독립성 중요 | Choreography |
| SPOF 제거 필요 | Choreography |

---

## 9. Spring Event vs Kafka

### Spring Event (현재 구현)

```java
// 발행
eventPublisher.publish(paymentCompletedEvent);

// 구독
@EventListener
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    // 처리
}
```

**특징:**
- 같은 JVM 내에서만 동작
- 메시지 유실 가능 (서버 재시작 시)
- 설정이 간단하다
- 동기/비동기 선택 가능 (`@Async`)

### Kafka

```java
// 발행
kafkaTemplate.send("payment-completed", orderId, paymentCompletedEvent);

// 구독
@KafkaListener(topics = "payment-completed", groupId = "ranking-service")
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    // 처리
}
```

**특징:**
- JVM 경계를 넘어 다른 서비스로 전달
- 메시지 영속화 (디스크 저장)
- 높은 처리량 (파티셔닝으로 병렬 처리)
- 장애 시에도 메시지 보존
- 재처리 가능

### 언제 뭘 쓰나?

| 상황 | 선택 | 이유 |
|------|------|------|
| 모놀리식, 단일 인스턴스 | Spring Event | 충분히 간단하고 빠르다 |
| 모놀리식, 다중 인스턴스 | Kafka | 인스턴스 간 이벤트 공유 필요 |
| MSA 구조 | Kafka | 서비스 간 통신 필수 |
| 유실되면 안 되는 이벤트 | Kafka | 영속화 보장 |
| 순간 트래픽 폭증 예상 | Kafka | 버퍼 역할 + 순차 처리 |
| 재처리가 필요한 경우 | Kafka | 오프셋 기반 재처리 |

### Redis vs Kafka 선택 기준

```
즉시 응답 필요? → Redis
재처리 필요? → Kafka
락 대기 감수 가능? → Redis 분산 락
대기 없이 빠른 응답? → Redis + Kafka 조합
```

---

## 10. 이커머스 활용 사례

### 선착순 쿠폰 발급: Redis + Kafka 조합

실무에서는 Redis와 Kafka를 조합해서 사용한다.

```
Redis: 동시성 제어, 수량 체크
Kafka: 비동기 후처리 (DB 저장 등 무거운 작업)
```

### INCR vs Set: 뭘 써야 하나?

먼저 비즈니스 요구사항을 따져봐야 한다.

**"몇 번째로 신청했는지"가 중요한가?**

- 1등 100만원, 2등 50만원, 3등 10만원 → **순위가 곧 보상**
- 이런 경우엔 INCR로 정확한 순서를 추적해야 한다

**"100명 안에 들었냐 아니냐"만 중요한가?**

- 선착순 100명에게 동일한 쿠폰 발급
- 1번째든 100번째든 같은 쿠폰
- 이런 경우엔 Set으로 충분하다

| 요구사항 | 선택 | 이유 |
|---------|------|------|
| 순위별 차등 보상 | INCR | 정확한 순서 추적 필요 |
| 동일 보상 (N명 한정) | Set | 수량 체크 + 중복 방지가 한 번에 |

### 방식 1: INCR 구조

```
1. 사용자 요청
      ↓
2. Redis INCR → "나는 57번째"
      ↓
   100명 초과? → 즉시 "마감" 응답
   100명 이하? → 계속 진행
      ↓
3. Kafka로 쿠폰 발급 메시지 발행
      ↓
4. 컨슈머가 DB 저장
```

INCR은 원자적 연산이라 동시성 문제가 없다. 근데 중복 발급 방지는 별도로 필요하다.

### 방식 2: Set 구조

```
1. 사용자 요청
      ↓
2. Redis SCARD → "현재 57명 발급됨"
   Redis SADD → 중복이면 실패, 아니면 Set에 추가
      ↓
   100명 초과? → 즉시 "마감" 응답
   중복 신청? → 즉시 "이미 발급" 응답
   통과? → 계속 진행
      ↓
3. Kafka로 쿠폰 발급 메시지 발행
      ↓
4. 컨슈머가 DB 저장
```

Set은 SCARD(수량 체크) + SADD(중복 방지)가 한 번에 된다. 순위별 차등이 없다면 이 방식이 더 간결하다.

### List에서 Kafka로 전환

기존에 Redis List를 대기열로 쓰던 구조가 있다면:

```
AS-IS: Set → List(RPUSH) → 스케줄러(LPOP) → DB
TO-BE: Set → Kafka 발행 → 컨슈머 → DB
```

List → Kafka 전환의 장점:
- 스케줄러 폴링 대신 이벤트 기반 처리
- 컨슈머 스케일 아웃으로 처리량 증가
- 메시지 영속화로 유실 방지
- MSA 전환 시 서비스 분리 용이

### 동시성 문제와 방어선

Set의 SCARD + SADD 사이에 race condition이 있다. 100명 한정인데 101명이 통과할 수 있다.

```
Thread A: SCARD → 99명
Thread B: SCARD → 99명
Thread A: SADD → 100명 (통과)
Thread B: SADD → 101명 (통과) ← race condition
```

**List 구조의 방어선**
```
Set에 101명 들어가도 → List에서 100개만 LPOP → 정확히 100명
```

**Kafka 구조의 방어선**
```
Set에 101명 들어가도 → 컨슈머가 DB 카운트 체크 → 100명 넘으면 무시
```

이게 비효율이 아니다. **분산 환경에서 최종 검증은 필수**다.

Outbox 패턴도 마찬가지다. 메시지 발행의 원자성을 보장하지만, 컨슈머에서 멱등성 체크(= DB 확인)는 여전히 필요하다. 분산 시스템에서 완벽한 원자성은 불가능하고, 어딘가에서 최종 방어를 해야 한다.

| 구조 | 방어선 위치 | 특징 |
|------|------------|------|
| Set + List | Redis List | 모놀리식에서 단순하고 효과적 |
| Set + Kafka | DB (컨슈머) | 분산 환경 확장성, DB 체크는 트레이드오프 |

방어선의 위치만 다를 뿐, 둘 다 "최종 검증"을 하는 건 같다.

### DB 레벨 최종 검증

반드시 함께 사용한다. Defense in Depth (심층 방어) 원칙:

```
1차 검증: Redis (INCR 또는 SCARD/SADD)
2차 검증: Kafka 컨슈머에서 한 번 더 체크
3차 검증: DB Unique 제약, 수량 체크
```

어느 한 레이어가 실패해도 다른 레이어가 방어한다.

### 결제 완료 후 처리

```
결제 완료 이벤트 발행
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│              payment-completed Topic                     │
└─────────────────────────────────────────────────────────┘
        │              │              │
        ▼              ▼              ▼
┌───────────────┐ ┌───────────┐ ┌──────────────┐
│ Ranking       │ │ 알림      │ │ 데이터       │
│ Service       │ │ Service   │ │ 분석         │
│ (랭킹 업데이트)│ │ (SMS/푸시)│ │ (로그 적재)  │
└───────────────┘ └───────────┘ └──────────────┘
```

- 각 서비스가 독립적인 Consumer Group으로 구독
- 결제 서비스는 발행만 하고 끝
- 새 기능 추가 시 새 Consumer Group만 추가

### 재고 차감 순서 보장

```
Key = productId로 발행

Product 1 요청들 → Partition 0 → 순서대로 처리
Product 2 요청들 → Partition 1 → 순서대로 처리
Product 3 요청들 → Partition 2 → 순서대로 처리
```

- 같은 상품 요청은 항상 같은 파티션
- 파티션 내에서 순서 보장
- 재고 차감 순서 꼬임 방지

---

## 11. 이벤트 발행 기준

### 핵심 질문

> "누군가 이 이벤트를 듣고 행동해야 하는가?"

**발행해야 하는 경우**
- 주문 완료 → 결제, 재고, 알림 서비스가 관심 있음
- 배송 대기 → 배송 서비스, 알림 서비스가 관심 있음

**발행하지 않아도 되는 경우**
- 내부 관리용 상태 변경
- 아무도 구독하지 않는 이벤트

### 과도한 이벤트 발행의 문제

- 컨슈머 부하 증가
- 대부분 노이즈가 됨
- 디버깅 복잡도 증가

**결론**: 모든 상태 변경마다 이벤트 발행 ❌ → 비즈니스 의미가 생기는 시점에만 발행 ✓

---

## 12. 성능 측정 지표

TPS만으로는 부족하다. 봐야 할 지표들:

**1. 스루풋 (Throughput)**
- 발행 TPS: 프로듀서 초당 발행 건수
- 처리 TPS: 컨슈머 초당 처리 건수
- 둘을 구분해서 봐야 함

**2. 레이턴시 (Latency)**
- 평균 레이턴시
- P95, P99 레이턴시 (상위 5%, 1%)

**3. 컨슈머 렉 ⭐ 가장 중요**
- 렉이 계속 증가 → 처리량 부족 신호

**4. 자원 사용률**
- Kafka: 디스크 I/O 중요
- 네트워크 I/O

**5. 에러율과 재시도 횟수**

---

## 13. MSA에서 메시지 스키마 관리

### 권장: 공통 DTO 모듈 분리

```
common-kafka-dto/
├── build.gradle
└── src/main/java/
    └── com/example/kafka/dto/
        ├── OrderCreatedEvent.java
        ├── PaymentCompletedEvent.java
        └── StockDeductedEvent.java
```

각 서비스에서 Gradle 의존성으로 추가:
```gradle
dependencies {
    implementation 'com.example:common-kafka-dto:1.0.0'
}
```

### Schema Registry를 권장하지 않는 이유

1. **SPOF 문제**: Schema Registry가 죽으면 전체 Kafka 통신 불가
2. **중앙 의존성**: MSA의 분산 철학에 역행
3. **운영 부담**: 별도 인프라 관리 필요

> Schema Registry는 Avro 등 스키마 기반 직렬화에 유용하지만, 단순한 JSON DTO라면 공통 모듈로 충분하다.

---

## 마치며

Kafka는 결국 메시지를 안전하게 저장하고, 순서대로, 여러 소비자에게 전달하는 시스템

### 핵심 개념 요약

- **Topic + Partition**: 메시지 저장 단위와 병렬 처리의 핵심
- **Producer + Consumer**: 발행/구독 모델 (서로 모르는 독립적인 존재)
- **Consumer Group**: 파티션을 나눠 읽는 논리적 그룹 (= 서비스 단위)
- **Offset**: 어디까지 읽었는지 추적
- **Replication**: 장애 대비 복제

### 기억할 것

- 브로커 클러스터에 여러 토픽 운영 (토픽마다 브로커 X)
- 브로커 최소 3대, RF=3, 처리량 20~30% 여유
- Redis = 동시성 제어, Kafka = 비동기 후처리 (조합해서 사용)
- **파티션은 줄일 수 없음** → 보수적으로 3개 시작
- 파티션 수 = 최대 동시 처리 성능 = 컨슈머 수
- 수동 커밋으로 처리 완료 시점에 커밋
- At-Least-Once + 멱등성 설계로 중복 방지
- **컨슈머 렉이 가장 중요한 모니터링 지표**
- Kafka 핵심 가치 = 관심사의 분리 (MSA)

---

## References

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring for Apache Kafka](https://spring.io/projects/spring-kafka)
- [카프카 핵심 가이드](https://product.kyobobook.co.kr/detail/S000201464167)
- [올바른 카프카 컨슈머(KafkaConsumer) 설정 가이드와 내부 동작 분석](https://mangkyu.tistory.com/432)
- [토스ㅣSLASH 24 - 보상 트랜잭션으로 분산 환경에서도 안전하게 환전하기](https://www.youtube.com/watch?v=xpwRTu47fqY)