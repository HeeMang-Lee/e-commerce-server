# 이커머스 동시성 문제, 어떻게 해결할까?

이커머스 서비스에서 가장 신경 써야 하는 게 동시성 문제다. 재고가 마이너스가 되거나, 포인트가 이상하게 차감되거나, 쿠폰이 초과 발급되면 큰 문제가 생긴다.

이 글에서는 프로젝트에서 발생할 수 있는 동시성 문제를 식별하고, 비관적 락과 낙관적 락을 언제 써야 하는지, 트랜잭션 범위를 어떻게 잡아야 하는지 정리해봤다.

## 유즈케이스부터 생각하자

동시성 제어를 고민할 때 가장 먼저 물어봐야 할 질문은 이거다.

**"이 데이터에 경합이 발생하는가?"**

### 경합이 없는 경우: 포인트

포인트는 사용자별로 독립적이다. 내 포인트를 내가 쓰는 건데, 다른 사람과 경합이 일어날 일이 없다.

```
사용자 A: 내 포인트 50,000원 차감
사용자 B: 내 포인트 30,000원 차감
→ 서로 다른 레코드, 경합 없음
```

이런 경우는 낙관적 락이 적합하다. 충돌이 거의 없으니까.

### 경합이 심한 경우: 재고

재고는 다르다. 인기 상품의 재고 5개를 만 명이 동시에 구매하려고 하면?

```
재고 5개
동시 요청: 10,000명
→ 같은 레코드를 1만 명이 동시에 수정, 경합 극심
```

이런 경우 낙관적 락을 쓰면 재앙이다.

## 낙관적 락의 함정

낙관적 락은 충돌이 발생하면 재시도를 한다. 그런데 경합이 심하면 재시도 횟수가 기하급수적으로 늘어난다.

**시나리오: 재고 5개, 동시 요청 10,000명**

```
1차 시도: 10,000명 동시 접근
→ 5명 성공, 9,995명 실패 (재시도)

2차 시도: 9,995명 재시도
→ 5명 성공, 9,990명 실패 (재시도)

3차 시도: 9,990명 재시도
→ 5명 성공, 9,985명 실패 (재시도)

...

총 재시도 횟수: 9,995 + 9,990 + 9,985 + ... ≈ 엄청난 부하
```

데이터베이스가 재시도 폭주로 터질 수 있다.

반면 비관적 락(SELECT FOR UPDATE)을 쓰면?

```
1차: 1명 락 획득 → 재고 차감 → 커밋 (다음 사람 대기)
2차: 1명 락 획득 → 재고 차감 → 커밋 (다음 사람 대기)
...
5차: 마지막 1명 락 획득 → 재고 차감 → 커밋

총 재시도 횟수: 0
대기는 있지만, 사람이 인지 못 할 수준 (수십 ms)
```

재시도가 없다. 순서대로 처리되니까.

## 그럼 언제 뭘 써야 하나?

### 비관적 락을 써야 하는 경우

- **재고 차감**
  - 여러 사용자가 같은 상품 재고를 동시에 차감
  - 경합이 극심함
  - SELECT FOR UPDATE로 락을 잡고 빠르게 처리

- **쿠폰 발급 (선착순)**
  - 제한 수량이 있는 쿠폰
  - 여러 사용자가 동시에 발급 시도
  - 초과 발급 방지를 위해 락 필요

### 낙관적 락을 써야 하는 경우

- **포인트 충전/차감**
  - 각 사용자가 자기 포인트만 관리
  - 경합이 없음
  - @Version으로 충돌 감지

- **쿠폰 사용**
  - 사용자가 자기 쿠폰만 사용
  - 경합이 없음

## 트랜잭션 범위의 함정

비관적 락을 쓸 때 가장 조심해야 할 게 트랜잭션 범위다.

### 주니어 개발자가 자주 하는 실수

결제 프로세스를 하나의 큰 트랜잭션으로 묶어버리는 것.

```java
@Transactional
public void processPayment(Long orderId) {
    // 1. 쿠폰 조회 및 사용
    // 2. 포인트 조회 및 사용
    // 3. 재고 조회 (SELECT FOR UPDATE) ← 여기서 락 잡힘
    // 4. 재고 차감
    // 5. 주문 정보 업데이트
    // 6. 결제 정보 생성
}
```

이게 뭐가 문제일까?

**재고 5개, 동시 요청 5,000명이라면?**

```
1번째 사람: 락 획득 → 300ms 동안 모든 작업 수행 → 커밋
2번째 사람: 300ms 대기 → 락 획득 → 300ms 작업 → 커밋
3번째 사람: 600ms 대기 → 락 획득 → 300ms 작업 → 커밋
...
5번째 사람: 1,200ms 대기 → 락 획득 → 300ms 작업 → 커밋

5,000번째 사람: (300ms × 4,999) = 1,499,700ms 대기 = 약 25분
```

**25분을 기다려야 재고 차감이 가능하다.**

이건 말이 안 된다. 락을 너무 오래 잡고 있어서 생기는 문제다.

### 정답: 트랜잭션을 쪼개자

비관적 락이 필요한 부분만 최소한으로 묶고, 나머지는 별도 트랜잭션으로 분리한다.

```java
// 트랜잭션 1: 재고 차감 (가장 짧게!)
@Transactional
public void reduceStock(Long productId, int quantity) {
    // SELECT FOR UPDATE
    // 재고 차감
    // 커밋 → 즉시 락 해제
}

// 트랜잭션 2: 포인트 사용
@Transactional
public void usePoint(Long userId, int amount) {
    // @Version으로 낙관적 락
    // 포인트 차감
    // 커밋
}

// 트랜잭션 3: 쿠폰 사용
@Transactional
public void useCoupon(Long userCouponId) {
    // @Version으로 낙관적 락
    // 쿠폰 사용
    // 커밋
}

// 트랜잭션 4: 주문 완료
@Transactional
public void completeOrder(Long orderId) {
    // 주문 상태 업데이트
    // 결제 정보 생성
    // 커밋
}
```

이제 재고 차감은 수십 ms 안에 끝나고, 다음 사람이 바로 처리할 수 있다.

**하지만 문제가 생겼다.**

중간에 실패하면? 재고는 차감됐는데 포인트 차감이 실패하면? 쿠폰 사용이 실패하면?

## 보상 트랜잭션이 필요하다

트랜잭션을 쪼개면 원자성이 깨진다. 그래서 실패 시 이미 성공한 트랜잭션들을 되돌리는 **보상 트랜잭션**이 필요하다.

```java
public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
    try {
        // 1. 재고 차감
        reduceStock(productId, quantity);

        // 2. 포인트 사용
        usePoint(userId, usedPoint);

        // 3. 쿠폰 사용
        useCoupon(userCouponId);

        // 4. 주문 완료
        completeOrder(orderId);

        return success();

    } catch (Exception e) {
        // 보상 트랜잭션: 이미 성공한 것들을 되돌림
        compensate(orderId, usedPoint, userCouponId);
        throw e;
    }
}

private void compensate(Long orderId, int usedPoint, Long userCouponId) {
    // 1. 재고 복구
    restoreStock(orderId);

    // 2. 포인트 복구
    if (usedPoint > 0) {
        refundPoint(userId, usedPoint);
    }

    // 3. 쿠폰 복구
    if (userCouponId != null) {
        restoreCoupon(userCouponId);
    }

    // 4. 결제 실패 상태로 변경
    failPayment(orderId);
}
```

복잡해졌지만, 성능은 훨씬 좋아진다.

## 현재 프로젝트의 문제점

### 1. 재고 관리 ✅

**잘 구현됨**

```java
// ProductRepository.executeWithLock() 사용
productRepository.executeWithLock(
    productId,
    product -> {
        product.reduceStock(quantity);
        return item;
    }
);
```

비관적 락으로 재고 차감을 보호하고 있다. 동시성 테스트도 통과한다.

### 2. 포인트 관리 ⚠️

**문제: 동시성 제어가 없음**

```java
// PointService.java
public PointResponse chargePoint(PointChargeRequest request) {
    User user = userRepository.getByIdOrThrow(request.userId());
    user.charge(request.amount());  // 동시성 제어 없음
    userRepository.save(user);
    ...
}
```

낙관적 락이 필요하다. User Entity에 @Version 컬럼을 추가해야 한다.

### 3. 쿠폰 발급 ✅

**잘 구현됨**

```java
// CouponRepository.executeWithLock() 사용
couponRepository.executeWithLock(
    couponId,
    coupon -> {
        coupon.issue();  // currentIssueCount 증가
        ...
    }
);
```

비관적 락으로 쿠폰 발급 수량을 제어하고 있다. 선착순 제한이 잘 작동한다.

**하지만 중복 발급 방지는 없다.** 같은 사용자가 같은 쿠폰을 여러 번 발급받을 수 있다.

→ `user_coupons` 테이블에 `UNIQUE KEY (user_id, coupon_id)` 제약 조건이 필요하다.

### 4. 결제 프로세스 ⚠️

**문제 1: 트랜잭션 경계가 명확하지 않음**

```java
// OrderService.processPayment()
// @Transactional이 없음!
public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
    // 각 repository 호출마다 별도 트랜잭션
    ...
}
```

메서드 레벨에 @Transactional이 없어서 각 DB 작업마다 별도 트랜잭션이 생성된다. 이것도 문제지만, 사실 이렇게 나뉘어 있는 게 성능상 더 나을 수도 있다.

하지만 의도적으로 분리한 게 아니라 그냥 없는 거라면 문제다.

**문제 2: 보상 트랜잭션이 불완전함**

```java
} catch (Exception e) {
    // 재고만 복구
    for (OrderItem item : orderItems) {
        productRepository.executeWithLock(
            item.getProductId(),
            product -> {
                product.restoreStock(item.getQuantity());
                return null;
            }
        );
    }

    // 문제: 포인트 복구 없음
    // 문제: 쿠폰 복구 없음

    payment.fail();
    orderPaymentRepository.save(payment);
    throw e;
}
```

재고만 복구하고 포인트와 쿠폰은 복구하지 않는다. 데이터 정합성이 깨진다.

## 개선 방향

### 1. 포인트에 낙관적 락 추가

```java
@Entity
public class User extends BaseTimeEntity {

    @Version
    private Long version;  // 낙관적 락

    private Money pointBalance;

    public void charge(int amount) {
        // 충돌 시 OptimisticLockException 발생
        // 재시도 로직 필요
    }
}
```

### 2. 트랜잭션 범위 명확화

```java
// Application Layer - 트랜잭션 없음
public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
    try {
        // Domain Service를 호출
        stockService.reduceStock(...);     // 트랜잭션 1
        pointService.usePoint(...);        // 트랜잭션 2
        couponService.useCoupon(...);      // 트랜잭션 3
        orderService.completeOrder(...);   // 트랜잭션 4
    } catch (Exception e) {
        compensationService.compensate(...);
        throw e;
    }
}
```

각 Domain Service가 자체 트랜잭션을 관리하도록 분리한다.

### 3. 완전한 보상 트랜잭션 구현

```java
@Service
public class CompensationService {

    @Transactional
    public void compensate(Long orderId, int usedPoint, Long userCouponId) {
        // 1. 재고 복구
        restoreStock(orderId);

        // 2. 포인트 복구 (추가 필요)
        if (usedPoint > 0) {
            User user = userRepository.getByIdOrThrow(userId);
            user.charge(usedPoint);  // 환불
            userRepository.save(user);

            PointHistory refund = new PointHistory(
                userId,
                TransactionType.REFUND,
                usedPoint,
                user.getPointBalance(),
                "결제 실패로 인한 포인트 환불",
                orderId
            );
            pointHistoryRepository.save(refund);
        }

        // 3. 쿠폰 복구 (추가 필요)
        if (userCouponId != null) {
            UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(userCouponId);
            userCoupon.restore();  // USED → AVAILABLE
            userCouponRepository.save(userCoupon);
        }

        // 4. 결제 실패
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);
        payment.fail();
        orderPaymentRepository.save(payment);
    }
}
```

### 4. 쿠폰 중복 발급 방지

```sql
ALTER TABLE user_coupons
ADD UNIQUE KEY uk_user_coupon (user_id, coupon_id);
```

DB 레벨에서 중복 발급을 막는다.

## 정리

동시성 문제를 해결할 때 기억할 것들:

1. **유즈케이스부터 생각하자**
   - 경합이 있는가?
   - 재고처럼 경합이 심하면 비관적 락
   - 포인트처럼 경합이 없으면 낙관적 락

2. **트랜잭션은 최대한 짧게**
   - 비관적 락이 필요한 부분만 묶는다
   - 나머지는 별도 트랜잭션으로 분리
   - 성능을 위해서는 Domain Service 또는 Repository 레벨까지 내린다

3. **보상 트랜잭션을 잊지 말자**
   - 트랜잭션을 쪼개면 원자성이 깨진다
   - 실패 시 이미 성공한 작업들을 되돌려야 한다
   - 복잡하지만 성능을 위해 필요하다

4. **테스트로 검증하자**
   - CountDownLatch로 동시성 테스트 작성
   - 재고가 마이너스가 되지 않는지
   - 포인트가 이상하게 차감되지 않는지
   - 쿠폰이 초과 발급되지 않는지

성능과 코드 복잡도 사이의 트레이드오프다. 편한 걸 선택하면 느려지고, 성능을 선택하면 복잡해진다. 하지만 이커머스에서는 성능이 더 중요하다.
