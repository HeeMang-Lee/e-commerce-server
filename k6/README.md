# K6 부하 테스트 스크립트

이 디렉토리에는 이커머스 서비스의 핵심 API에 대한 K6 부하 테스트 스크립트가 포함되어 있습니다.

## 설치

```bash
# macOS
brew install k6

# Linux
sudo apt-get install k6

# Windows
choco install k6
```

## 테스트 스크립트

| 파일 | 시나리오 | 목표 VUs | 설명 |
|------|---------|---------|------|
| `coupon-issue-test.js` | 선착순 쿠폰 | 10,000 | 동시성 제어 및 처리량 검증 |
| `order-create-test.js` | 주문 생성 | 2,000 | 재고 차감 시 분산락 검증 |
| `payment-test.js` | 결제 처리 | 1,000 | 포인트+쿠폰 통합 처리 |
| `popular-products-test.js` | 인기 상품 | 1,000 | 캐시 효과 검증 |
| `point-charge-test.js` | 포인트 충전 | 100x10 | 동일 사용자 동시 요청 |
| `run-all-tests.js` | 통합 테스트 | 다양 | 모든 시나리오 순차 실행 |

## 실행 방법

### 개별 테스트 실행

```bash
# 선착순 쿠폰 발급 테스트 (10,000명 시뮬레이션)
k6 run coupon-issue-test.js

# 환경 변수로 설정 변경
k6 run -e BASE_URL=http://localhost:8080 -e COUPON_ID=1 coupon-issue-test.js

# 주문 생성 테스트
k6 run order-create-test.js

# 결제 테스트
k6 run payment-test.js

# 인기 상품 조회 테스트
k6 run popular-products-test.js

# 포인트 충전 테스트
k6 run point-charge-test.js
```

### 통합 테스트 실행

```bash
# 모든 시나리오 순차 실행
k6 run run-all-tests.js
```

### Prometheus 메트릭 연동

```bash
# Prometheus로 메트릭 전송
k6 run --out experimental-prometheus-rw coupon-issue-test.js

# 환경 변수 설정
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
k6 run --out experimental-prometheus-rw coupon-issue-test.js
```

## 결과 파일

테스트 결과는 `results/` 디렉토리에 JSON 형식으로 저장됩니다:

```
results/
├── coupon-issue-result.json
├── order-create-result.json
├── payment-result.json
├── popular-products-result.json
├── point-charge-result.json
└── all-tests-result.json
```

## 테스트 전 준비사항

### 1. 테스트 데이터 생성

테스트 실행 전 다음 데이터가 필요합니다:

```sql
-- 사용자 10,000명 생성
INSERT INTO user (name, email)
SELECT
    CONCAT('user', seq),
    CONCAT('user', seq, '@test.com')
FROM (SELECT @row := @row + 1 as seq FROM
    (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
    (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
    (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t3,
    (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t4,
    (SELECT @row := 0) r
) nums
WHERE seq <= 10000;

-- 상품 10종 (재고 각 1,000개)
INSERT INTO product (name, price, stock) VALUES
('상품1', 10000, 1000),
('상품2', 20000, 1000),
-- ... (생략)

-- 쿠폰 (수량 500개)
INSERT INTO coupon (name, discount_amount, total_quantity, remaining_quantity)
VALUES ('선착순쿠폰', 1000, 500, 500);

-- 각 사용자 포인트 100,000원
INSERT INTO point (user_id, balance)
SELECT id, 100000 FROM user;
```

### 2. 서버 실행 확인

```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# API 응답 확인
curl http://localhost:8080/api/products/popular
```

## 성능 목표 (SLO)

| 지표 | 목표값 |
|-----|--------|
| TPS | > 500 req/s |
| P50 응답시간 | < 100ms |
| P95 응답시간 | < 500ms |
| P99 응답시간 | < 2,000ms |
| 에러율 | < 1% (비즈니스 오류 제외) |

## 트러블슈팅

### "connection refused" 에러
- 서버가 실행 중인지 확인
- BASE_URL 환경 변수 확인

### "too many open files" 에러
```bash
# macOS/Linux
ulimit -n 65535
```

### 메모리 부족
```bash
# K6에 더 많은 메모리 할당
K6_OUT=json=results.json k6 run --vus 10000 coupon-issue-test.js
```
