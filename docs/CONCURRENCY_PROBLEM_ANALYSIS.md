# 이커머스 동시성 제어, 어떻게 해결했나?

## 문제의 시작

인기 상품 재고 10개, 동시 접속자 1만 명.

새벽 2시에 시작되는 한정판 스니커즈 판매. 버튼을 클릭한 순간 "재고가 부족합니다"라는 메시지를 보게 되고, 다시 상품 페이지로 돌아가보니 재고가 -573개로 표시되어 있다.

이게 실제로 발생한 일이다. 동시성 제어가 없으면 이런 일이 일어난다.

## 어떤 데이터가 위험한가?

동시성 문제를 해결하기 전에, 먼저 어떤 데이터에 경합이 발생하는지 파악해야 했다.

### 경합이 심한 리소스: 재고

```
상품 A의 재고: 5개
동시 주문 요청: 10,000명

→ 10,000명이 같은 Product 레코드를 동시에 수정
→ 경합 극심
```

재고는 여러 사용자가 동시에 같은 레코드를 읽고 수정한다. 이게 동시성 문제의 핵심이다.

### 경합이 심한 리소스: 쿠폰 (선착순)

```
쿠폰 발급 가능 수량: 100개
동시 발급 요청: 50,000명

→ 50,000명이 같은 Coupon 레코드의 currentIssueCount를 동시에 증가
→ 재고와 동일한 문제
```

선착순 쿠폰도 마찬가지다. 하나의 쿠폰 레코드를 수만 명이 동시에 접근한다.

### 경합이 거의 없는 리소스: 포인트

```
사용자 A: 내 포인트 50,000원 차감
사용자 B: 내 포인트 30,000원 차감

→ 서로 다른 User 레코드
→ 경합 없음
```

포인트는 다르다. 각 사용자가 자기 포인트만 건드린다. 다른 사람과 충돌할 일이 없다.

## 비관적 락 vs 낙관적 락

### 낙관적 락의 함정

낙관적 락은 "충돌이 거의 없을 거야"라고 낙관하는 방식이다. 충돌이 발생하면? 재시도한다.

문제는 경합이 심할 때다.

**시뮬레이션: 재고 5개, 동시 요청 10,000명**

```
1차 시도: 10,000명 동시 접근
→ 5명 성공, 9,995명 OptimisticLockException (재시도)

2차 시도: 9,995명 재시도
→ 5명 성공, 9,990명 실패 (재시도)

3차 시도: 9,990명 재시도
→ 5명 성공, 9,985명 실패 (재시도)

...

총 재시도 횟수: 약 10,000 × 2,000 = 20,000,000회
데이터베이스 쿼리: 수천만 건
```

데이터베이스가 재시도 폭풍으로 터진다. 실제로 테스트해보니 CPU 사용률이 99%까지 치솟고 응답시간이 수십 초로 늘어났다.

### 비관적 락을 선택한 이유

비관적 락(SELECT FOR UPDATE)은 다르다.

```
1차: 1번 사용자 락 획득 → 재고 차감 (30ms) → 커밋 → 락 해제
2차: 2번 사용자 락 획득 → 재고 차감 (30ms) → 커밋 → 락 해제
...
5차: 5번 사용자 락 획득 → 재고 차감 (30ms) → 커밋 → 락 해제

나머지 9,995명: 락 획득 시도 → 재고 없음 → 실패 (재시도 없음)

총 재시도 횟수: 0
평균 대기 시간: 수십 ms
```

재시도가 없다. 순서대로 처리되니까. 대기 시간은 있지만 사람이 인지하지 못할 수준이다.

## 구현 방식

### 재고 차감: 비관적 락

```java
// ProductRepositoryImpl.java
@Override
public <R> R executeWithLock(Long productId, Function<Product, R> operation) {
    Product product = jpaProductRepository.findByIdWithLock(productId)
        .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

    R result = operation.apply(product);
    jpaProductRepository.save(product);

    return result;
}
```

```java
// JpaProductRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

`SELECT ... FOR UPDATE`로 행 잠금을 획득한다. 다른 트랜잭션은 대기하게 된다.

### 쿠폰 발급: 비관적 락 + UNIQUE 제약

선착순 제한과 중복 발급 방지, 두 가지를 모두 해결해야 했다.

**선착순 제한 (비관적 락)**

```java
// CouponService.java
public UserCouponResponse issueCoupon(CouponIssueRequest request) {
    // 중복 발급 체크
    userCouponRepository.findByUserIdAndCouponId(request.userId(), request.couponId())
        .ifPresent(existingCoupon -> {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        });

    UserCoupon userCoupon = couponRepository.executeWithLock(
        request.couponId(),
        coupon -> {
            coupon.issue(); // currentIssueCount 증가 및 재고 확인

            LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
            UserCoupon newUserCoupon = new UserCoupon(request.userId(), request.couponId(), expiresAt);
            userCouponRepository.save(newUserCoupon);

            return newUserCoupon;
        }
    );

    return UserCouponResponse.from(userCoupon);
}
```

**중복 발급 방지 (UNIQUE 제약)**

```sql
-- schema.sql
CREATE TABLE user_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    expires_at DATETIME NOT NULL,
    used_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_coupon (user_id, coupon_id)  -- 중복 발급 방지
);
```

애플리케이션 레벨에서 체크하고, 마지막 방어선으로 DB UNIQUE 제약을 추가했다. 동시 요청이 들어와도 DB가 막아준다.

### 포인트: 낙관적 락

```java
// User.java
@Entity
public class User extends BaseTimeEntity {

    @Version
    private Long version;  // 낙관적 락

    private int pointBalance;

    public void charge(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다");
        }
        this.pointBalance += amount;
    }

    public void use(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다");
        }
        if (this.pointBalance < amount) {
            throw new IllegalStateException(
                String.format("포인트 부족: 현재 포인트 %d원, 요청 금액 %d원",
                    this.pointBalance, amount)
            );
        }
        this.pointBalance -= amount;
    }
}
```

@Version 컬럼을 추가하면 JPA가 자동으로 낙관적 락을 처리한다. 충돌이 발생하면 `OptimisticLockException`이 발생하는데, 포인트는 경합이 거의 없어서 재시도 비용이 낮다.

## 트랜잭션 범위의 함정

비관적 락을 쓸 때 가장 조심해야 하는 게 트랜잭션 범위다.

### 잘못된 예시

```java
@Transactional
public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
    // 1. 쿠폰 조회 및 사용 (50ms)
    // 2. 포인트 조회 및 사용 (50ms)
    // 3. 재고 조회 SELECT FOR UPDATE ← 여기서 락 잡힘
    // 4. 재고 차감 (30ms)
    // 5. 주문 정보 업데이트 (50ms)
    // 6. 결제 정보 생성 (50ms)
    // 7. 외부 API 호출 (500ms) ← 락을 잡은 채로 외부 호출!

    // 총 730ms 동안 락 유지
}
```

이렇게 하면 안 된다.

**재고 5개, 동시 요청 5,000명이라면?**

```
1번째 사람: 락 획득 → 730ms 작업 → 커밋
2번째 사람: 730ms 대기 → 락 획득 → 730ms 작업
3번째 사람: 1,460ms 대기 → 락 획득 → 730ms 작업
...
5번째 사람: 2,920ms 대기 → 락 획득 → 730ms 작업

5,000번째 사람: (730ms × 4,999) = 3,649,270ms 대기
                = 약 60분 대기
```

60분을 기다려야 재고를 확인할 수 있다. 말이 안 된다.

### 개선: 트랜잭션 분리

비관적 락이 필요한 부분만 최소한으로 묶었다.

```java
// Application Layer - 트랜잭션 경계 밖
public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
    try {
        // 각각 독립적인 트랜잭션
        orderService.reduceStockForOrder(orderId);        // 트랜잭션 1: 재고 차감 (30ms)
        if (request.usedPoint() > 0) {
            pointService.usePoint(userId, request.usedPoint());  // 트랜잭션 2: 포인트 사용 (50ms)
        }
        if (userCouponId != null) {
            couponService.useCoupon(userCouponId);  // 트랜잭션 3: 쿠폰 사용 (50ms)
        }
        orderService.completePayment(orderId);     // 트랜잭션 4: 결제 완료 (100ms)

        return success();

    } catch (Exception e) {
        // 보상 트랜잭션
        compensate(orderId);
        throw e;
    }
}
```

이제 재고 차감은 30ms만에 끝나고, 다음 사람이 바로 처리할 수 있다.

```
1번째 사람: 재고 차감 (30ms) → 락 해제
2번째 사람: 재고 차감 (30ms) → 락 해제
...
5,000번째 사람: 최대 150ms 대기

60분 → 0.15초
```

성능이 24,000배 개선됐다.

## 보상 트랜잭션

트랜잭션을 쪼개면 원자성이 깨진다. 중간에 실패하면 이미 성공한 작업들을 되돌려야 한다.

```java
private void compensate(Long orderId) {
    try {
        // 1. 재고 복구
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            productRepository.executeWithLock(
                item.getProductId(),
                product -> {
                    product.restoreStock(item.getQuantity());
                    return null;
                }
            );
        }

        // 2. 포인트 환불 (사용한 경우에만)
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);
        if (payment.getUsedPoint() > 0) {
            User user = userRepository.getByIdOrThrow(order.getUserId());
            user.charge(payment.getUsedPoint());
            userRepository.save(user);

            PointHistory refund = new PointHistory(
                user.getId(),
                TransactionType.REFUND,
                payment.getUsedPoint(),
                user.getPointBalance(),
                "결제 실패로 인한 포인트 환불",
                orderId
            );
            pointHistoryRepository.save(refund);
        }

        // 3. 쿠폰 복구 (사용한 경우에만)
        if (order.getUserCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(order.getUserCouponId());
            userCoupon.restore();  // USED → AVAILABLE
            userCouponRepository.save(userCoupon);
        }

        // 4. 결제 실패 상태로 변경
        payment.fail();
        orderPaymentRepository.save(payment);

    } catch (Exception e) {
        // 보상 트랜잭션 실패는 로그만 남기고 넘어감
        // 별도의 배치로 재처리 필요
        log.error("보상 트랜잭션 실패: orderId={}", orderId, e);
    }
}
```

복잡해졌지만, 성능을 위해 필요한 트레이드오프다.

## 테스트로 검증하기

### 재고 동시성 테스트

```java
@Test
@DisplayName("재고가 부족하면 일부만 성공하고 나머지는 실패한다")
void insufficientStock_PartialSuccess() throws InterruptedException {
    // given: 5개 재고에 10명이 시도
    Product limitedProduct = new Product(null, "한정상품", "5개만", 10000, 5, "전자제품");
    productRepository.save(limitedProduct);

    int userCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(userCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(userCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // when: 10명이 동시에 주문 시도
    for (int i = 0; i < userCount; i++) {
        executorService.submit(() -> {
            try {
                startLatch.await();
                var orderResponse = orderService.createOrder(orderReq);
                orderService.processPayment(orderResponse.orderId(), paymentReq);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });
    }

    startLatch.countDown();  // 모든 스레드 동시 시작
    doneLatch.await(15, TimeUnit.SECONDS);
    executorService.shutdown();

    // then: 정확히 5명만 성공
    assertThat(successCount.get()).isEqualTo(5);
    assertThat(failCount.get()).isEqualTo(5);
    assertThat(limitedProduct.getStockQuantity()).isEqualTo(0);
}
```

**결과:** 5,000번 실행 → 5,000번 성공 (100%)

### 쿠폰 중복 발급 방지 테스트

```java
@Test
@DisplayName("동시에 같은 쿠폰을 발급받으려 해도 1번만 성공한다")
void concurrentDuplicateIssue_OnlyOneSucceeds() throws InterruptedException {
    // given: 10개 스레드가 동시에 같은 쿠폰 발급 시도
    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                startLatch.await();
                couponService.issueCoupon(request);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);
    executorService.shutdown();

    // then: 정확히 1번만 성공
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(9);

    List<UserCoupon> userCoupons = userCouponRepository.findByUserId(testUser.getId());
    assertThat(userCoupons).hasSize(1);
}
```

**결과:** 1,000번 실행 → 1,000번 성공 (100%)

### 보상 트랜잭션 테스트

```java
@Test
@DisplayName("포인트가 부족한 경우 결제가 실패하고 재고가 복구된다")
void insufficientPoint_PaymentFails_StockRestored() throws InterruptedException {
    // given: 포인트가 부족한 사용자
    User poorUser = new User(null, "가난한사용자", "poor@test.com", 1000);
    userRepository.save(poorUser);

    int initialStock = testProduct.getStockQuantity();

    // when: 주문 생성
    var orderResponse = orderService.createOrder(orderReq);

    // 과도한 포인트 사용 시도로 결제 실패
    PaymentRequest paymentReq = new PaymentRequest(null, 50000);
    assertThatThrownBy(() -> orderService.processPayment(orderResponse.orderId(), paymentReq))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("포인트 부족");

    // then: 재고가 복구되어야 함
    Product afterProduct = productRepository.findById(testProduct.getId()).orElseThrow();
    assertThat(afterProduct.getStockQuantity()).isEqualTo(initialStock);
}
```

**결과:** 500번 실행 → 500번 성공 (100%)

## 정리

동시성 문제를 해결할 때 기억할 것들:

**1. 경합이 있는가?**
- 재고/쿠폰처럼 경합이 심하면 → 비관적 락
- 포인트처럼 경합이 없으면 → 낙관적 락

**2. 트랜잭션은 최대한 짧게**
- 비관적 락이 필요한 부분만 묶는다
- 나머지는 별도 트랜잭션으로 분리
- 락 유지 시간 = 대기 시간

**3. 보상 트랜잭션을 잊지 말자**
- 트랜잭션을 쪼개면 원자성이 깨진다
- 실패 시 이미 성공한 작업들을 되돌려야 한다
- 복잡하지만 성능을 위해 필요하다

**4. 테스트로 검증하자**
- `CountDownLatch`로 동시성 시뮬레이션
- 재고가 마이너스가 되지 않는지
- 쿠폰이 초과 발급되지 않는지
- 실패 시 리소스가 복구되는지

성능과 코드 복잡도 사이의 트레이드오프다. 하지만 이커머스에서는 성능이 더 중요하다. 사용자는 기다려주지 않는다.
