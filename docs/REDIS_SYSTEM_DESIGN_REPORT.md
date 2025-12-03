# Redis 기반 시스템 설계 회고

## 들어가며

이커머스 서비스에서 "선착순 쿠폰 발급"과 "인기 상품 랭킹"은 꽤 흔한 기능이다. 처음엔 DB만으로 충분하다고 생각했는데, 트래픽이 몰리는 상황을 시뮬레이션해보니 이야기가 달라졌다.

---

## 1. 선착순 쿠폰, 왜 Redis였나

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
요청 → Redis Set 검증 → 대기열 추가 → 즉시 응답
        ↓
      스케줄러 → 벌크 DB Insert
```

Redis Set으로 중복 체크(SISMEMBER)하고, 통과하면 List에 넣고 바로 응답한다. DB 저장은 스케줄러가 5초마다 100건씩 처리한다.

| 테스트 케이스 | 기존 | Redis 방식 | 개선 |
|-------------|------|-----------|------|
| 1,000명 / 100쿠폰 | 21초 | **3.9초** | 5.4배 |
| 10,000명 / 1,000쿠폰 | 121초 | **35초** | 3.4배 |

처리량이 47 req/s에서 256 req/s로 뛰었다.

### 자료구조 선택

멘토링에서 "Redis는 자료구조 선택이 절반"이라는 얘기를 들었다. 쿠폰 발급엔 두 가지가 필요했다.

**Set** - 누가 발급받았는지
```
coupon:1:issued = {user1, user2, user3, ...}
```
- 중복 체크: SISMEMBER → O(1)
- 발급 수량: SCARD → O(1)

**List** - DB 저장 대기열
```
coupon:queue = ["1:user1", "1:user2", ...]
```
- 추가: RPUSH → O(1)
- 처리: LPOP → O(1)

처음엔 "수량 제한을 어떻게 원자적으로 처리하지?" 고민했는데, SADD 후 SCARD로 확인하고 초과하면 SREM으로 롤백하는 방식으로 해결했다.

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

### TTL 설정

올리브영은 TTL 60초를 썼다. 근데 우리 서비스는 "3일간 인기 상품"이다. 1-2건 팔린다고 순위가 확 바뀌진 않는다.

TTL을 10분으로 늘렸다. 로컬 캐시 히트율이 올라가고 Redis 부하가 줄었다.

```java
public static final int CACHE_TTL_SECONDS = 600;  // 10분
```

데이터 특성에 맞게 TTL을 조정하는 게 중요하다는 걸 배웠다.

### Self-Invocation 문제

`@Cacheable`이 동작 안 해서 한참 삽질했다.

```java
public List<ProductResponse> getTopProducts(int limit) {
    long version = getCurrentVersion();
    return getTopProductsByVersion(limit, version);  // @Cacheable 무시됨
}
```

같은 클래스 내부 호출은 AOP 프록시를 안 탄다. `@Lazy` self-injection으로 해결했다.

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

---

## 3. 배운 것들

**Redis 자료구조 선택이 설계의 절반이다**
- 쿠폰: Set(중복방지) + List(대기열)
- 랭킹: Sorted Set(점수 기반 정렬)

**캐시 일관성은 단순하게**
- Pub/Sub보다 버전 기반이 더 안정적인 경우도 있다
- 메시지 유실 가능성을 항상 고려하자

**TTL은 데이터 특성에 맞게**
- "천천히 변하는 데이터"는 TTL을 길게 가져가도 된다

**Spring AOP의 self-invocation 함정**
- 같은 클래스 내부 호출은 프록시를 안 탄다
- `@Lazy` self-injection이나 별도 클래스 분리로 해결

---

## 4. 성능 요약

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

다음엔 Redis Cluster 환경에서의 동작이나, 실제 프로덕션 레벨의 모니터링도 고민해보고 싶다.
