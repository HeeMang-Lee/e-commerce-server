# 쿠폰 발급 시스템: Kafka 적용 보고서

## 개요

선착순 쿠폰 발급에 Kafka를 적용했다. 이 문서는 Kafka가 어떻게 구성되어 있고, 메시지가 어떻게 흘러가는지 설명한다.

---

## 전체 구조


```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Producer                                   │
│  KafkaCouponIssueService.issue(userId, couponId)                        │
│      └── kafkaTemplate.send("coupon-issue", couponId.toString(), event) │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Kafka Broker                                    │
│                                                                         │
│  Topic: coupon-issue (파티션 3개)                                         │
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
│                    Consumer Group: coupon-issue-service                 │
│                                                                         │
│  현재: Consumer 1개가 3개 파티션 모두 처리                                     │
│  ┌─────────────────────────────────────────────────────┐                │
│  │ CouponKafkaConsumer                                 │                │
│  │   - Partition 0, 1, 2 담당                           │                │
│  │   - 메시지 수신 → DB 저장                               │                │
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
│                                                 │
│  Consumer A                                     │
│    ├── Partition 0 담당                          │
│    ├── Partition 1 담당                          │
│    └── Partition 2 담당                          │
│                                                 │
└─────────────────────────────────────────────────┘

┌─ 확장 시 (Consumer 3개) ──────────────────────────┐
│                                                 │
│  Consumer A → Partition 0 담당                   │
│  Consumer B → Partition 1 담당                   │
│  Consumer C → Partition 2 담당                   │
│                                                 │
└─────────────────────────────────────────────────┘
```

Consumer 4개로 늘리면? → 1개는 놀게 됨 (파티션보다 많으므로)

### 권장 구성

| 환경 | 파티션 수 | Consumer 수 | 비고 |
|------|----------|-------------|------|
| 개발/테스트 | 3 | 1 | 단일 서버로 충분 |
| 운영 | 3 | 3 | 파티션 = Consumer로 최대 병렬 처리 |

> **핵심**: 파티션 수 = Consumer 수일 때 최대 처리량. Consumer가 파티션보다 많으면 놀게 되고, 적으면 한 Consumer가 여러 파티션을 처리한다.

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

### @RetryableTopic으로 재시도 + DLT

```java
// CouponKafkaConsumer.java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2),  // 지수 백오프
    dltStrategy = DltStrategy.FAIL_ON_ERROR
)
@KafkaListener(topics = "coupon-issue", groupId = "coupon-issue-service")
public void consume(CouponIssueEvent event) {
    // 비즈니스 로직
}

@DltHandler
public void handleDlt(CouponIssueEvent event) {
    log.error("[DLT] 쿠폰 발급 최종 실패 - couponId={}, userId={}",
            event.couponId(), event.userId());
    // TODO: Slack 알림 연동
}
```

### 동작 방식

```
메시지 처리 실패
    ↓
1초 후 재시도 (1회) → coupon-issue-retry-0 토픽
    ↓
2초 후 재시도 (2회) → coupon-issue-retry-1 토픽
    ↓
4초 후 재시도 (3회) → coupon-issue-retry-2 토픽
    ↓
3번 다 실패하면
    ↓
coupon-issue-dlt 토픽으로 이동 → @DltHandler 실행
```

지수 백오프(1초→2초→4초)로 상대 서버 회복 시간을 준다.

### Consumer의 멱등성

```java
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

## DLT 재처리

### 전체 흐름

```
Consumer 처리 실패
    │
    ▼
@RetryableTopic (1초 → 2초 → 4초, 3회)
    │
    ▼ 3회 다 실패
DLT 토픽 → @DltHandler
    │
    ▼
FailedEvent 테이블에 저장
    │
    ▼
DltRetryScheduler (10초마다 체크)
    │
    ├── nextRetryAt이 지났으면 재처리
    │       ├── 성공 → RECOVERED
    │       └── 실패 → 지수 백오프로 다음 재시도 예약
    │
    └── 3회 실패 → ABANDONED (수동 처리 필요)
```

### DLT 메시지 DB 저장

```java
// CouponKafkaConsumer.java
@DltHandler
@Transactional
public void handleDlt(CouponIssueEvent event) {
    log.error("[DLT] 쿠폰 발급 최종 실패 - couponId={}, userId={}",
            event.couponId(), event.userId());

    String payload = objectMapper.writeValueAsString(event);
    FailedEvent failedEvent = new FailedEvent(
            KafkaConfig.TOPIC_COUPON_ISSUE,
            event.couponId().toString(),
            payload,
            "DLT 도달: 3회 재시도 실패"
    );
    failedEventRepository.save(failedEvent);
}
```

### FailedEvent 엔티티

```java
@Entity
@Table(name = "failed_events")
public class FailedEvent {
    private String topic;
    private String eventKey;
    private String payload;
    private String errorMessage;
    private FailedEventStatus status;  // PENDING, RETRYING, RECOVERED, ABANDONED
    private Integer retryCount;
    private Integer maxRetryCount;     // 기본값 3
    private LocalDateTime nextRetryAt; // 지수 백오프 기반
    private LocalDateTime createdAt;
    private LocalDateTime lastRetryAt;
    private LocalDateTime recoveredAt;

    // 지수 백오프: 30초 → 1분 → 2분 → 4분...
    public void scheduleNextRetry() {
        long delaySeconds = (long) (30 * Math.pow(2, retryCount));
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
    }
}
```

### 재처리 스케줄러

```java
// DltRetryScheduler.java
@Scheduled(fixedDelay = 10000)  // 10초마다 체크
@Transactional
public void retryFailedEvents() {
    // nextRetryAt이 지난 이벤트만 조회
    List<FailedEvent> events = repository.findRetryableEventsNow(LocalDateTime.now());

    for (FailedEvent event : events) {
        event.retry();
        try {
            if (processEvent(event)) {
                event.markAsRecovered();
            } else {
                event.markAsFailed("재처리 실패");  // 내부에서 scheduleNextRetry() 호출
            }
        } catch (Exception e) {
            event.markAsFailed(e.getMessage());
        }
        repository.save(event);
    }
}
```

### 지수 백오프 타임라인

```
DLT 도달 (0회 실패)
    │
    └── nextRetryAt = now + 30초
           │
           ▼ 30초 후
        1회 재시도 실패
           │
           └── nextRetryAt = now + 60초 (30 * 2^1)
                  │
                  ▼ 1분 후
               2회 재시도 실패
                  │
                  └── nextRetryAt = now + 120초 (30 * 2^2)
                         │
                         ▼ 2분 후
                      3회 재시도 실패
                         │
                         └── status = ABANDONED
                             (수동 처리 필요)
```

### 금융권 패턴과 비교

금융권에서는 DL 토픽 자체를 저장소로 사용하는 패턴도 있다.

| 구분 | DL 토픽 방식 (금융권) | DB 방식 (현재 구현) |
|------|----------------------|-------------------|
| 실패 저장 | DL 토픽 (Kafka) | FailedEvent 테이블 (DB) |
| 재시도 주체 | 별도 DL 서버 | 스케줄러 (같은 서버) |
| 재시도 방식 | DL 토픽 폴링 → 재발행 | DB 조회 → 직접 처리 |
| 재시도 간격 | 지수 백오프 | 지수 백오프 |
| 인프라 | Kafka + DL 서버 | Kafka + DB |

**DL 토픽 방식:**
```
DLT → DL 서버가 토픽 폴링 → 시간 됐으면 재처리 → 실패하면 다시 DLT에 발행
```

**현재 구현 (DB 방식):**
```
DLT → @DltHandler → DB 저장 → 스케줄러가 DB 폴링 → 재처리
```

### 왜 DB 방식을 선택했나

1. **단일 서버 환경**: 별도 DL 서버 없이 스케줄러로 충분
2. **지연 처리가 간단**: `nextRetryAt` 컬럼으로 쉽게 구현
3. **관리 편의성**: SQL로 실패 이벤트 조회/수동 처리 가능

### 확장 시 고려사항

트래픽이 많아지고 서버가 분산되면 DL 토픽 방식으로 전환할 수 있다:

1. 별도 DL Consumer 서버 구성
2. DL 토픽에서 직접 폴링
3. 메시지 헤더에 retry count, next retry time 저장
4. Kafka 인프라 내에서 완결

현재는 단일 서버 + DB 방식으로 충분하고, 핵심인 **지수 백오프**는 동일하게 적용되어 상대 서버에 회복 시간을 준다.

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

---

## 정리

### 구현한 것

| 항목 | 내용 |
|------|------|
| 동시성 제어 | Redis Lua Script로 원자적 수량 체크 |
| 비동기 처리 | Kafka로 DB 저장 분리 |
| 순서 보장 | couponId를 키로 사용해 같은 쿠폰은 같은 파티션 |
| 재시도 | @RetryableTopic으로 3회 재시도 + 지수 백오프 |
| DLT 재처리 | DB 저장 후 스케줄러가 지수 백오프로 재시도 |
| 멱등성 | Consumer에서 중복 발급 체크 |

### 아키텍처 결정

| 결정 | 이유 |
|------|------|
| Redis + Kafka 조합 | Redis로 빠른 응답, Kafka로 안정적 저장 |
| couponId를 파티션 키로 | 같은 쿠폰 요청을 순차 처리해 race condition 방지 |
| DLT → DB 저장 | 단일 서버 환경에서 간단하게 지연 재처리 구현 |
| 파티션 3개 | 현재는 Consumer 1개지만, 확장 시 최대 3개까지 병렬 처리 가능 |

### 한계와 개선 방향

- **현재**: 단일 서버에서 스케줄러로 DLT 재처리
- **확장 시**: 별도 DL 서버 + DL 토픽 방식으로 전환 가능
- **모니터링**: Kafka Lag, 실패 이벤트 수 등 메트릭 추가 필요

---

## Reference

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring for Apache Kafka](https://docs.spring.io/spring-kafka/reference/)
- [Kafka 파티션과 Consumer Group](https://kafka.apache.org/documentation/#intro_consumers)
- [Redis Lua Scripting](https://redis.io/docs/interact/programmability/eval-intro/)
