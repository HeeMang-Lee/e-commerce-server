# 쿠폰 발급 시스템: Kafka 적용 보고서

## 개요

선착순 쿠폰 발급에 Kafka를 적용했다. 이 문서는 Kafka가 어떻게 구성되어 있고, 메시지가 어떻게 흘러가는지 설명한다.

---

## 전체 구조

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Producer                                    │
│  KafkaCouponIssueService.issue(userId, couponId)                        │
│      └── kafkaTemplate.send("coupon-issue", couponId.toString(), event) │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Kafka Broker                                     │
│                                                                          │
│  Topic: coupon-issue (파티션 3개)                                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                     │
│  │ Partition 0  │ │ Partition 1  │ │ Partition 2  │                     │
│  │              │ │              │ │              │                     │
│  │ couponId=3   │ │ couponId=1   │ │ couponId=2   │                     │
│  │ couponId=6   │ │ couponId=4   │ │ couponId=5   │                     │
│  │ couponId=9   │ │ couponId=7   │ │ couponId=8   │                     │
│  │     ...      │ │     ...      │ │     ...      │                     │
│  └──────────────┘ └──────────────┘ └──────────────┘                     │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Consumer Group: coupon-issue-service                  │
│                                                                          │
│  현재: Consumer 1개가 3개 파티션 모두 처리                               │
│  ┌─────────────────────────────────────────────────────┐                │
│  │ CouponKafkaConsumer                                 │                │
│  │   - Partition 0, 1, 2 담당                          │                │
│  │   - 메시지 수신 → DB 저장                           │                │
│  └─────────────────────────────────────────────────────┘                │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 토픽 설정

```java
// KafkaConfig.java
@Bean
public NewTopic couponIssueTopic() {
    return TopicBuilder.name("coupon-issue")
            .partitions(3)    // 파티션 3개
            .replicas(1)      // 복제본 1개 (단일 브로커)
            .build();
}
```

| 설정 | 값 | 의미 |
|------|-----|------|
| partitions | 3 | 병렬 처리 단위. Consumer를 최대 3개까지 확장 가능 |
| replicas | 1 | 복제본 수. 운영 환경에서는 3 권장 |

---

## 키(Key)와 파티션 배치

### 메시지 발행 코드

```java
// KafkaCouponIssueService.java
CouponIssueEvent event = new CouponIssueEvent(couponId, userId);
kafkaTemplate.send("coupon-issue", couponId.toString(), event);
//                  └─ 토픽         └─ 키              └─ 값
```

### 키가 파티션을 결정한다

Kafka는 키의 해시값으로 파티션을 결정한다:

```
partition = hash(key) % 파티션수
```

예시 (파티션 3개):
```
couponId=1  → hash("1") % 3 = 1  → Partition 1
couponId=2  → hash("2") % 3 = 2  → Partition 2
couponId=3  → hash("3") % 3 = 0  → Partition 0
couponId=4  → hash("4") % 3 = 1  → Partition 1
couponId=5  → hash("5") % 3 = 2  → Partition 2
```

### 왜 couponId를 키로 사용하나?

**같은 쿠폰에 대한 모든 요청이 같은 파티션으로 간다.**

```
couponId=1에 대한 요청 1000개
    ↓
모두 Partition 1로 전달
    ↓
순차적으로 처리됨 (1→2→3→...→1000)
    ↓
DB에서 정확한 수량 제어 가능
```

만약 userId를 키로 사용하면:
```
couponId=1에 대한 요청 1000개 (userId 각각 다름)
    ↓
Partition 0, 1, 2에 분산
    ↓
3개 파티션에서 동시에 처리
    ↓
DB 수량 체크 시 race condition 발생 가능
```

---

## Consumer Group

### 현재 구성

```java
// CouponKafkaConsumer.java
@KafkaListener(
    topics = "coupon-issue",
    groupId = "coupon-issue-service",  // Consumer Group 이름
    containerFactory = "kafkaListenerContainerFactory"
)
public void consume(CouponIssueEvent event) { ... }
```

### Consumer Group의 역할

**같은 Group 내 Consumer들은 파티션을 나눠 가진다.**

```
Consumer Group: coupon-issue-service

┌─ 현재 (Consumer 1개) ─────────────────────────────┐
│                                                    │
│  Consumer A                                        │
│    ├── Partition 0 담당                            │
│    ├── Partition 1 담당                            │
│    └── Partition 2 담당                            │
│                                                    │
└────────────────────────────────────────────────────┘

┌─ 확장 시 (Consumer 3개) ──────────────────────────┐
│                                                    │
│  Consumer A → Partition 0 담당                     │
│  Consumer B → Partition 1 담당                     │
│  Consumer C → Partition 2 담당                     │
│                                                    │
└────────────────────────────────────────────────────┘
```

Consumer 4개로 늘리면? → 1개는 놀게 됨 (파티션보다 많으므로)

### 다른 Consumer Group은 같은 메시지를 받는다

payment-completed 토픽의 경우:

```
┌─────────────────────────────────────────────────────┐
│  Topic: payment-completed                           │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐      │
│  │ Partition 0│ │ Partition 1│ │ Partition 2│      │
│  └────────────┘ └────────────┘ └────────────┘      │
└─────────────────────────────────────────────────────┘
           │                           │
           ▼                           ▼
┌─────────────────────┐    ┌─────────────────────────┐
│ Group: ranking-     │    │ Group: data-platform-   │
│        service      │    │        service          │
│                     │    │                         │
│ RankingKafkaConsumer│    │ DataPlatformKafkaConsumer│
│ → 랭킹 Redis 기록   │    │ → 외부 API 전송         │
└─────────────────────┘    └─────────────────────────┘
```

**같은 메시지를 두 Group이 모두 받는다.** 이게 Kafka로 이벤트 기반 아키텍처를 구현하는 핵심.

---

## Redis와의 조합

### 흐름

```
1. 사용자 요청
       │
       ▼
2. Redis Lua Script (원자적 체크)
   ┌─────────────────────────────────────┐
   │ SISMEMBER → 이미 발급? → ALREADY    │
   │ SCARD → 수량 초과? → SOLD_OUT       │
   │ SADD → Set에 추가 → SUCCESS         │
   └─────────────────────────────────────┘
       │
       ▼ (SUCCESS인 경우만)
3. Kafka 발행
   kafkaTemplate.send("coupon-issue", couponId, event)
       │
       ▼
4. Consumer가 DB 저장
   - 멱등성 체크 (이미 있으면 skip)
   - 수량 체크 (초과면 skip)
   - UserCoupon 저장
   - Coupon 카운트 증가
```

### 왜 Redis를 앞에 두나?

1. **빠른 응답**: 사용자는 Redis 결과만 받고 돌아감
2. **트래픽 필터링**: SOLD_OUT, ALREADY_ISSUED는 Kafka까지 안 감
3. **중복 방지**: Set으로 같은 사용자 중복 요청 차단

### Redis race condition 해결

기존 문제:
```java
// 이 사이에 다른 요청이 끼어들 수 있음
Long count = redisTemplate.opsForSet().size(key);  // 체크
Long added = redisTemplate.opsForSet().add(key, userId);  // 추가
```

Lua Script로 해결:
```lua
-- 원자적으로 실행됨
if redis.call('SISMEMBER', key, userId) == 1 then return 'ALREADY' end
if redis.call('SCARD', key) >= max then return 'SOLD_OUT' end
redis.call('SADD', key, userId)
return 'SUCCESS'
```

---

## 에러 처리

### Dead Letter Queue

```java
// KafkaConfig.java
@Bean
public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
}
```

동작 방식:
```
메시지 처리 실패
    ↓
1초 후 재시도 (최대 3번)
    ↓
3번 다 실패하면
    ↓
coupon-issue.DLT 토픽으로 이동
```

### Consumer의 멱등성

```java
// CouponKafkaConsumer.java
public void consume(CouponIssueEvent event) {
    // 이미 발급되었으면 skip (멱등성)
    if (userCouponRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
        return;
    }
    // ...
}
```

같은 메시지가 여러 번 와도 한 번만 처리된다.

---

## Profile 분리

```java
// Redis 모드 (기본)
@Profile("!kafka")
@Service("asyncCouponIssueService")
public class AsyncCouponIssueService implements CouponIssuer { ... }

// Kafka 모드
@Profile("kafka")
@Service("kafkaCouponIssueService")
public class KafkaCouponIssueService implements CouponIssuer { ... }
```

실행:
```bash
# Redis 모드
./gradlew bootRun

# Kafka 모드
docker-compose up -d kafka
./gradlew bootRun --args='--spring.profiles.active=kafka'
```

---

## 성능 특성

### 5000명 동시 요청, 500개 쿠폰

| 항목 | Redis | Kafka |
|------|-------|-------|
| 요청 처리 | 1,701 ms | 1,385 ms |
| DB 저장 | 1,171 ms | 7,345 ms |
| **전체** | **2,872 ms** | **8,730 ms** |
| 정확성 | 500/500 | 500/500 |

### 특성 비교

| 항목 | Redis | Kafka |
|------|-------|-------|
| 처리 속도 | 빠름 (배치) | 느림 (개별 트랜잭션) |
| P99 Latency | 426 ms | 233 ms |
| 메시지 영속성 | 메모리 | 디스크 |
| 확장성 | 단일 인스턴스 | 파티션/Consumer 확장 |
| 다른 서비스 연동 | 어려움 | Consumer Group 추가 |

---

## 언제 Kafka를 쓰나?

**Redis만으로 충분한 경우:**
- 단일 서비스
- 빠른 전체 처리 완료가 필요
- 인프라 단순화 우선

**Kafka가 필요한 경우:**
- 쿠폰 발급 이벤트를 다른 서비스가 구독 (알림, 정산 등)
- 메시지 유실 방지가 중요
- Consumer 확장으로 처리량 증가 필요
- 피크 시간대 사용자 응답 속도 우선 (P99 개선)
