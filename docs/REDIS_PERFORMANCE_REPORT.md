# Redis 기반 성능 개선 보고서

## 1. 선착순 쿠폰 발급 성능 비교

### 테스트 환경
- Spring Boot 3.2.0
- Redis 7.0 (Testcontainers)
- MySQL 8.0 (Testcontainers)
- ExecutorService (ThreadPool)

### 성능 비교 결과

| 테스트 케이스 | 기존 방식 (분산락+DB) | Redis 비동기 방식 | 성능 향상 |
|-------------|---------------------|------------------|----------|
| **1,000명 / 100쿠폰** | 21,097ms (47 req/s) | **3,905ms (256 req/s)** | **5.4배** |
| **5,000명 / 500쿠폰** | 69,061ms (72 req/s) | **24,543ms (204 req/s)** | **2.8배** |
| **10,000명 / 1,000쿠폰** | 121,451ms (82 req/s) | **35,462ms (282 req/s)** | **3.4배** |

### 처리량 비교
```
기존 방식: 47~82 req/sec
Redis 방식: 204~282 req/sec
→ 평균 3~5배 성능 향상
```

### 아키텍처 변경
```
[기존] 요청 → 분산락 획득 → DB 트랜잭션 → 응답
[Redis] 요청 → Set 검증 → 대기열 추가 → 즉시 응답
         ↓
        스케줄러 → 벌크 DB Insert
```

---

## 2. 상품 랭킹 성능 측정

### Redis Sorted Set 기반 랭킹

| 연산 | 처리량/응답시간 | 비고 |
|------|----------------|------|
| **판매 기록 (ZINCRBY)** | 2,743 ops/sec | 10,000건 동시 기록 |
| **Top 5 조회 (ZREVRANGE)** | 평균 1.44ms | 1,000회 조회 |
| **3일 합산 조회 (ZUNIONSTORE)** | 평균 5.84ms | 100회 조회 |

### 특징
- 판매 기록: O(log N) 시간복잡도
- 순위 조회: O(log N + M) 시간복잡도
- 실시간 랭킹 업데이트 가능

---

## 3. Redis 자료구조 선택

### 선착순 쿠폰
```
Set: coupon:{couponId}:issued
  - SISMEMBER: 중복 체크 O(1)
  - SCARD: 수량 확인 O(1)
  - SADD: 발급 기록 O(1)

List: coupon:queue
  - RPUSH: 대기열 추가 O(1)
  - LPOP: 처리 O(1)
```

### 상품 랭킹
```
Sorted Set: ranking:daily:{yyyyMMdd}
  - ZINCRBY: 판매량 증가 O(log N)
  - ZREVRANGE: Top N 조회 O(log N + M)
  - ZUNIONSTORE: 여러 일자 합산 O(N) + O(M log M)
```

---

## 4. 테스트 일자
- 측정일: 2025-12-03
- 테스트 환경: macOS, Testcontainers

---

## 5. 결론

1. **쿠폰 발급**: Redis Set + List 조합으로 **3~5배 성능 향상**
2. **상품 랭킹**: Redis Sorted Set으로 **실시간 랭킹** 가능 (평균 응답 < 6ms)
3. **DB 부하 감소**: 비동기 벌크 처리로 DB 트랜잭션 최소화
