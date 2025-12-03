# Redis 기반 시스템 설계 회고

## 들어가며

이커머스 서비스에서 "선착순 쿠폰 발급"과 "인기 상품 랭킹"은 꽤 흔한 기능이다. 처음엔 DB만으로 충분하다고 생각했는데, 트래픽이 몰리는 상황을 시뮬레이션해보니 이야기가 달라졌다.

---

## 1. 선착순 쿠폰, Redis 자료구조 활용

### 기존 방식의 한계

처음 구현은 분산락 + DB 트랜잭션 조합이었다.

```
요청 → 분산락 획득 대기 → DB 트랜잭션 → 응답
```

문제는 락을 기다리는 시간이었다. 1,000명이 동시에 요청하면 999명은 줄을 서야 한다.

| 테스트 케이스 | 처리 시간 | 처리량 |
|-------------|----------|--------|
| 1,000명 / 100쿠폰 | 21초 | 47 req/s |
| 10,000명 / 1,000쿠폰 | 121초 | 82 req/s |

2분이나 걸린다. 사용자 입장에선 "발급 버튼 눌렀는데 화면이 안 넘어간다"는 경험을 하게 된다.

### Redis로 바꾸니까

핵심은 "일단 빠르게 응답하고, DB 저장은 나중에"였다.

```
┌─────────┐     ┌─────────────┐     ┌───────┐
│ Client  │────▶│  API Server │────▶│ Redis │
└─────────┘     └──────┬──────┘     └───────┘
                       │
                ┌──────▼──────┐     ┌───────┐
                │  Scheduler  │────▶│ MySQL │
                └─────────────┘     └───────┘
```

Redis Set으로 중복 체크하고, 통과하면 List에 넣고 바로 응답한다. DB 저장은 스케줄러가 5초마다 100건씩 처리한다.

| 테스트 케이스 | 기존 | Redis 방식 | 개선 |
|-------------|------|-----------|------|
| 1,000명 / 100쿠폰 | 21초 | **3.9초** | 5.4배 |
| 10,000명 / 1,000쿠폰 | 121초 | **35초** | 3.4배 |

처리량이 47 req/s에서 256 req/s로 뛰었다.

### 자료구조 선택: Set + List

Redis는 자료구조 선택이 절반이다.

**Step 1: Set으로 검증**
```
coupon:1:issued = {user1, user2, user3, ...}
```
- SCARD: 현재 발급된 수량 확인 (한정 수량 체크)
- SADD: 중복 발급 방지 (이미 발급받은 사용자 체크)

**Step 2: 대기열에 추가**
```
coupon:queue = ["1:user1", "1:user2", ...]
```
- 검증 통과 시 List에 추가 (RPUSH)
- 스케줄러가 LPOP으로 꺼내서 DB에 저장

### 분산락이 필요한가?

**불필요하다.** INCR, DECR, SADD 등은 모두 원자적 연산이다. 이런 연산을 하겠다고 따로 분산락을 걸 필요 없다.

기존에 분산락을 썼던 건 DB 트랜잭션 때문이었다. Redis 원자적 연산으로 대체하면 락 없이도 동시성 문제가 해결된다.

---

## 2. 상품 랭킹과 캐시 일관성

### Sorted Set으로 실시간 랭킹

"3일간 많이 팔린 상품 Top 5"를 구현해야 했다. DB 집계 쿼리로도 가능하지만, 매번 GROUP BY + ORDER BY 하기엔 부담스럽다.

Redis Sorted Set을 선택했다.

```
ranking:daily:20251203 = {상품1: 50, 상품2: 30, 상품3: 10}
```

- 판매 기록: ZINCRBY → O(log N)
- Top 5 조회: ZREVRANGE → O(log N + 5)
- 3일 합산: ZUNIONSTORE → O(N)

### ZUNIONSTORE는 언제 호출하나?

3일치 데이터를 합산하는 ZUNIONSTORE를 언제 호출할지 고민했다.

**방법 1: 조회할 때마다** - 비효율적. 같은 결과를 반복 계산한다.

**방법 2: 배치로 미리 합산** - 결과를 어디에 저장하지? 또 다른 키가 필요하다.

**선택한 방법: 조회 시 합산 + 로컬 캐시**

```
조회 요청 → 로컬 캐시 확인 → 없으면 ZUNIONSTORE → 캐시 저장
```

ZUNIONSTORE 결과는 임시 키에 저장했다가 바로 삭제한다. 어차피 로컬 캐시에 올라가니까 Redis에 남길 필요 없다.

```java
redisTemplate.opsForZSet().unionAndStore(keys.get(0), keys.subList(1, keys.size()), COMBINED_KEY);
Set<TypedTuple<String>> result = redisTemplate.opsForZSet().reverseRangeWithScores(COMBINED_KEY, 0, limit - 1);
redisTemplate.delete(COMBINED_KEY);  // 임시 키 삭제
return result;
```

### 상품 정보 조회 - MGET 활용

랭킹에서 상품 ID 목록을 가져온 후, 상품 상세 정보가 필요하다. 이때 N번 조회하면 N번의 네트워크 왕복이 발생한다.

```java
// Bad: N번 왕복
for (Long id : productIds) {
    Product p = productRepository.findById(id);
}

// Good: 1번 왕복
List<Product> products = productRepository.findAllById(productIds);
```

Redis에서도 마찬가지다. 상품 정보를 Redis에 캐싱한다면 MGET으로 한 번에 조회한다.

```
MGET product:1 product:2 product:3 product:4 product:5
```

5개 상품을 5번 GET 하면 5ms, MGET으로 한 번에 하면 1ms. 단순하지만 효과적이다.

성능 측정 결과:
- 판매 기록: 2,743 ops/sec
- Top 5 조회: 평균 1.44ms
- 3일 합산: 평균 5.84ms

### 로컬 캐시 도입

Redis 조회가 평균 6ms면 충분히 빠르다. 근데 트래픽이 몰리면 Redis도 부하를 받는다.

여기서 Caffeine 로컬 캐시를 추가했다. 문제는 **캐시 일관성**이었다.

### Pub/Sub vs 버전 기반

처음엔 Redis Pub/Sub으로 캐시 무효화를 구현하려고 했다.

```
판매 발생 → Redis Pub/Sub 발행 → 모든 서버 캐시 삭제
```

근데 고민이 생겼다.

1. **메시지 유실**: Pub/Sub은 구독자가 없으면 메시지가 사라진다. 서버 재시작 중이면?
2. **네트워크 순간 단절**: 그 사이 발행된 메시지는?
3. **복잡도**: 리스너 관리, 에러 핸들링...

올리브영 기술 블로그에서 "버전 기반 캐시 일관성" 패턴을 봤다. 생각보다 단순했다.

```
Redis: ranking:version = 3

조회 시:
1. 버전 조회 (숫자 하나라 빠름)
2. 로컬 캐시에서 "5_3" 키로 조회
3. 없으면 Redis 조회 후 저장
```

버전이 바뀌면 새 캐시 키로 조회하니까 자연스럽게 무효화된다. Pub/Sub 유실 걱정도 없다.

### 버전 증가 타이밍

버전을 언제 올릴지도 고민이었다.

**판매마다 올리면?** - 캐시 효과가 없다. 매번 새 키로 조회하니까.

**명시적 호출만?** - 관리자가 일일이 호출해야 한다. 비현실적이다.

**선택한 방법: 스케줄러 + 명시적 호출**

```java
@Scheduled(cron = "0 */10 * * * *")  // 10분마다
public void refreshRankingVersion() {
    rankingRedisRepository.incrementVersion();
}
```

기본적으로 10분마다 버전을 올린다. "3일간 인기 상품"은 1-2건 판매로 순위가 크게 바뀌지 않으니 10분 주기면 충분하다.

추가로 명시적 호출 API도 열어둔다. 대규모 프로모션 종료 후 즉시 반영이 필요할 때 사용한다.

### TTL 설정

올리브영은 TTL 60초를 썼다. 근데 우리 서비스는 "3일간 인기 상품"이다. 1-2건 팔린다고 순위가 확 바뀌진 않는다.

TTL을 10분으로 늘렸다. 로컬 캐시 히트율이 올라가고 Redis 부하가 줄었다.

```java
public static final int CACHE_TTL_SECONDS = 600;  // 10분
```

데이터 특성에 맞게 TTL을 조정하는 게 중요하다는 걸 배웠다.

### Self-Invocation 문제

`@Cacheable`이 동작하지 않는 문제가 있었다.

```java
public List<ProductResponse> getTopProducts(int limit) {
    long version = getCurrentVersion();
    return getTopProductsByVersion(limit, version);  // @Cacheable 무시됨
}
```

같은 클래스 내부 호출은 AOP 프록시를 타지 않는다. `@Lazy` self-injection으로 해결했다.

```java
private final ProductRankingService self;

public ProductRankingService(..., @Lazy ProductRankingService self) {
    this.self = self;
}

public List<ProductResponse> getTopProducts(int limit) {
    long version = getCurrentVersion();
    return self.getTopProductsByVersion(limit, version);  // 프록시 경유
}
```

**대안들:**
- 캐시 로직을 별도 클래스로 분리 (더 깔끔하지만 클래스가 늘어남)
- `CacheManager`를 직접 주입해서 수동 처리 (유연하지만 보일러플레이트 증가)

self-injection이 약간 트릭 같긴 한데, 기존 구조를 크게 안 바꿔도 돼서 선택했다.

---

## 3. 장애 대응

### Redis 장애 시

Redis 연결 실패하면 DB로 fallback한다.

```java
try {
    return rankingRedisRepository.getTopProductsLast3Days(limit);
} catch (Exception e) {
    log.warn("Redis 조회 실패, DB Fallback: {}", e.getMessage());
    return getTopProductsFromDB(limit);
}
```

쿠폰 발급도 마찬가지. Redis 장애 시엔 기존 분산락 방식으로 돌아가게 해두면 서비스는 느려도 유지된다.

### 모니터링 포인트

운영 시 봐야 할 지표들:
- Redis 커넥션 풀 사용률
- 대기열(coupon:queue) 길이 - 급격히 늘면 스케줄러 문제
- 캐시 히트율 - 낮으면 TTL이나 키 전략 재검토
- 버전 증가 빈도 - 너무 잦으면 캐시 효과 감소

---

## 4. 배운 것들

**Redis 자료구조 선택이 설계의 절반이다**
- 쿠폰: Set(중복방지 + 수량체크) + List(대기열)
- 랭킹: Sorted Set(점수 기반 정렬)

**Redis 원자적 연산으로 분산락 대체**
- SCARD, SADD 등은 원자적 연산
- 별도 분산락 없이 동시성 문제 해결

**배치 조회는 MGET으로**
- N번 왕복 vs 1번 왕복, 단순하지만 효과적

**캐시 일관성은 단순하게**
- Pub/Sub보다 버전 기반이 더 안정적인 경우도 있다
- 메시지 유실 가능성을 항상 고려하자

**TTL은 데이터 특성에 맞게**
- "천천히 변하는 데이터"는 TTL을 길게 가져가도 된다

**Spring AOP의 self-invocation 함정**
- 같은 클래스 내부 호출은 프록시를 타지 않는다
- `@Lazy` self-injection이나 별도 클래스 분리로 해결

---

## 5. 성능 요약

### 쿠폰 발급
| 지표 | 기존 (분산락+DB) | Redis 비동기 |
|------|-----------------|-------------|
| 처리량 | 47~82 req/s | 204~282 req/s |
| 10,000명 처리 | 121초 | 35초 |

### 상품 랭킹
| 연산 | 성능 |
|------|------|
| 판매 기록 | 2,743 ops/sec |
| Top 5 조회 | 1.44ms |
| 3일 합산 | 5.84ms |
| 버전 조회 | < 1ms |

---

## 마치며

"Redis 쓰면 빨라진다"는 말은 많이 들었는데, 직접 측정해보니 체감이 달랐다. 단순히 빠른 게 아니라, 아키텍처 자체가 바뀌면서 병목이 해소되는 느낌이었다.

---

## References

- [Redis 공식 문서 - Data types](https://redis.io/docs/data-types/)
- [Redis 공식 문서 - Sorted Sets](https://redis.io/docs/data-types/sorted-sets/)
- [올리브영 - 선물하기 프로모션 적용기: 멀티 레이어 캐시](https://oliveyoung.tech/2024-12-10/present-promotion-multi-layer-cache/)
- [카카오페이증권 - Redis on Kubernetes](https://tech.kakaopay.com/post/kakaopaysec-redis-on-kubernetes/)
