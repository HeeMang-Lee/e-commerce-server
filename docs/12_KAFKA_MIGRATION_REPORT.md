# 쿠폰 발급 시스템: Redis → Redis + Kafka 전환 보고서

## 문제 상황

선착순 쿠폰 발급에서 Redis Set의 분리된 연산이 race condition을 유발한다.

```java
// 기존 문제 코드
Long currentCount = redisTemplate.opsForSet().size(issuedKey);
if (currentCount >= maxQuantity) return SOLD_OUT;
// ← 여기서 다른 요청이 끼어들면?
Long added = redisTemplate.opsForSet().add(issuedKey, userIdStr);
```

`size()` 체크와 `add()` 사이에 race condition이 발생한다. 500개 한정인데 510명이 통과하거나, 480명만 통과할 수 있다.

---

## 해결: Lua Script로 원자적 연산

Redis는 Lua 스크립트를 **원자적으로 실행**한다. 스크립트 실행 중에는 다른 명령이 끼어들 수 없다.

```lua
local issuedKey = KEYS[1]
local userId = ARGV[1]
local maxQuantity = tonumber(ARGV[2])

-- 이미 발급받았는지 체크
if redis.call('SISMEMBER', issuedKey, userId) == 1 then
    return 'ALREADY_ISSUED'
end

-- 수량 체크
if redis.call('SCARD', issuedKey) >= maxQuantity then
    return 'SOLD_OUT'
end

-- Set에 추가
redis.call('SADD', issuedKey, userId)
return 'SUCCESS'
```

이 스크립트로 `SISMEMBER`, `SCARD`, `SADD`가 하나의 원자적 연산으로 실행된다. race condition이 완전히 제거된다.

---

## 구조 비교

### Redis 단일 (Lua Script 적용)

```
요청 → Redis Lua Script (원자적 체크+추가) → Redis List (큐) → 스케줄러 폴링 → DB 저장
```

### Redis + Kafka

```
요청 → Redis Lua Script (원자적 체크+추가) → Kafka 발행 → Consumer → DB 저장
```

두 방식 모두 Lua Script를 사용하므로 **정확성은 동일**하다. 차이는 비동기 처리 방식.

---

## 성능 비교

### 5000명 동시 요청, 500개 쿠폰 (Lua Script 적용 후)

| 항목 | Redis | Kafka |
|------|-------|-------|
| Phase 1 (요청 처리) | 1,701 ms | 1,385 ms |
| Phase 2 (DB 저장) | 1,171 ms | 7,345 ms |
| **전체 시간** | **2,872 ms** | **8,730 ms** |
| SUCCESS 응답 | 500 | 500 |
| **실제 DB 발급** | **500** | **500** |
| TPS | 2,939 /sec | 3,611 /sec |
| P50 Latency | 43 ms | 39 ms |
| P99 Latency | 426 ms | 233 ms |

### Lua Script 적용 전 (참고)

| 항목 | Redis | Kafka |
|------|-------|-------|
| SUCCESS 응답 | 499 | 495 |
| **실제 DB 발급** | **400** | **495** |

Lua Script 적용 전에는 race condition으로 정확한 수량 제어가 안 됐다.

---

## 분석

### Phase 1 (요청 처리)

Kafka가 약간 빠르다. 둘 다 Redis Lua Script를 사용하지만:
- Redis: Lua Script + List push
- Kafka: Lua Script + Kafka send (비동기)

Kafka send가 비동기라서 요청 스레드가 빨리 돌아간다.

### Phase 2 (DB 저장)

Redis가 6배 빠르다.

**Redis가 빠른 이유:**
- 배치 처리 (100건씩 한 번에 DB 저장)
- 같은 JVM 내에서 처리
- 트랜잭션 횟수 감소

**Kafka가 느린 이유:**
- 메시지마다 개별 트랜잭션
- 네트워크 구간 추가 (Producer → Broker → Consumer)

### P99 Latency

Kafka가 낮다 (233ms vs 426ms). Kafka는 요청을 빠르게 받고 백그라운드에서 처리하기 때문.

---

## 트레이드오프

### Redis가 좋은 경우

1. **전체 처리 시간이 중요한 경우**
   - 배치까지 완료하는 시간이 3배 빠름
   - 쿠폰 발급 후 바로 다음 이벤트가 있는 경우

2. **인프라 단순화**
   - Kafka 클러스터 운영 부담 없음
   - Redis만으로 충분한 규모

3. **단일 서비스**
   - 쿠폰 발급 이벤트를 다른 서비스가 구독하지 않는 경우

### Kafka가 좋은 경우

1. **메시지 영속성이 필요한 경우**
   - Kafka는 메시지를 디스크에 저장
   - 서버 재시작해도 메시지 보존
   - Redis List는 메모리 기반

2. **MSA 환경**
   - 여러 서비스가 쿠폰 발급 이벤트를 구독해야 할 때
   - 알림 서비스, 정산 서비스, 마케팅 서비스 등

3. **확장성이 필요한 경우**
   - 파티션 수만큼 Consumer 확장 가능
   - 트래픽 급증 시 Consumer만 추가

4. **P99 Latency가 중요한 경우**
   - 사용자 응답은 빠르게, DB 저장은 백그라운드에서
   - 피크 시간대 사용자 경험 개선

---

## 구현 방식: Profile 분리

기존 코드를 삭제하지 않고 Profile로 분리했다.

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

실행 방법:
```bash
# Redis 모드 (기본)
./gradlew bootRun

# Kafka 모드
docker-compose up -d kafka
./gradlew bootRun --args='--spring.profiles.active=kafka'
```

---

## Kafka 키 설계

쿠폰 발급에서 **couponId**를 키로 선택한 이유:

| 키 후보 | 장점 | 단점 |
|--------|------|------|
| couponId | 같은 쿠폰 요청이 순차 처리 | 인기 쿠폰에 부하 집중 (hot partition) |
| userId | 부하 분산 균일 | 같은 쿠폰이 여러 파티션에 분산 |

선착순 쿠폰에서는 **couponId**가 적합:
- 같은 쿠폰에 대한 Consumer 처리가 순차적
- DB 수량 체크가 일관성 있게 동작
- hot partition 문제는 파티션 수 증가로 대응 가능

---

## 개선 방향

### Kafka Consumer 배치 처리

현재 Consumer는 메시지마다 트랜잭션을 열어 느리다. 배치 처리로 개선 가능:

```java
@KafkaListener(
    topics = "coupon-issue",
    containerFactory = "batchListenerFactory"
)
public void consumeBatch(List<CouponIssueEvent> events) {
    Map<Long, List<CouponIssueEvent>> grouped = events.stream()
        .collect(Collectors.groupingBy(CouponIssueEvent::couponId));

    for (var entry : grouped.entrySet()) {
        processCouponBatch(entry.getKey(), entry.getValue());
    }
}
```

예상 효과: Phase 2 시간 50% 이상 단축

---

## 결론

| 기준 | 선택 |
|------|------|
| 전체 처리 속도 | Redis |
| P99 Latency | Kafka |
| 메시지 영속성 | Kafka |
| 인프라 단순함 | Redis |
| MSA 확장성 | Kafka |

**정확성은 Lua Script로 동일하게 보장된다.**

두 방식의 차이는 비동기 처리 방식과 인프라 복잡도. 단일 서비스에서 빠른 처리가 필요하면 Redis, MSA 환경에서 이벤트 기반 아키텍처가 필요하면 Kafka를 선택하면 된다.
