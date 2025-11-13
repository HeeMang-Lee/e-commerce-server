# 주문 내역 조회가 느린 이유

이커머스 API에서 사용자 주문 내역을 조회하는 기능이 있다. 개발 환경에서는 괜찮았는데, 주문이 쌓일수록 점점 느려질 것 같다는 불안감이 들었다.

실제로 코드를 다시 보니 예상이 맞았다. 전형적인 N+1 문제였다.

## 코드를 보고 느낀 위화감

`OrderService.getOrderHistory` 메서드를 보던 중, 뭔가 이상한 냄새가 났다.

```java
public List<OrderHistoryResponse> getOrderHistory(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId);
    return orders.stream()
            .map(order -> {
                // 반복문 안에서 데이터베이스 조회...?
                List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                return OrderHistoryResponse.from(order, items);
            })
            .toList();
}
```

반복문 안에서 데이터베이스를 호출하고 있었다. 주문이 100개면 데이터베이스를 101번 왕복한다.

### 실제로 실행되는 쿼리

Hibernate 로그를 켜보니 예상대로였다.

```sql
-- 1번: 사용자의 주문 조회
SELECT * FROM orders WHERE user_id = 1;

-- N번: 각 주문마다 아이템 조회 (무한 반복...)
SELECT * FROM order_items WHERE order_id = 1;
SELECT * FROM order_items WHERE order_id = 2;
SELECT * FROM order_items WHERE order_id = 3;
...
SELECT * FROM order_items WHERE order_id = 100;
```

주문 100개를 조회하는데 **101번의 쿼리**가 실행됐다. 주문이 1000개면 1001번, 10000개면 10001번이다.

문제는 네트워크 왕복이다. 로컬에서는 괜찮아 보여도, 운영 환경에서 AWS RDS 같은 원격 데이터베이스를 쓰면 왕복마다 수 ms씩 걸린다. 101번이면 수백 ms가 추가된다.

## 간접 참조를 쓰는데 왜 N+1이 발생했을까?

프로젝트는 이미 간접 참조로 설계되어 있었다.

```java
// Order.java - OrderItem 객체를 직접 참조하지 않음
@Column(name = "user_id")
private Long userId;  // ID만 저장

// OrderItem.java - Order 객체를 직접 참조하지 않음
@Column(name = "order_id")
private Long orderId;  // ID만 저장
```

간접 참조는 JPA의 Lazy Loading N+1을 예방한다. 하지만 Service 레벨에서 명시적으로 Repository를 호출하는 건 막지 못한다.

```java
// 이건 막아줌 (JPA Lazy Loading)
@OneToMany(fetch = FetchType.LAZY)
private List<OrderItem> orderItems;
order.getOrderItems();  // 여기서 N+1

// 이건 못 막음 (명시적 호출)
orderItemRepository.findByOrderId(order.getId());  // N+1 발생
```

간접 참조는 좋은 설계지만, Service 코드를 잘못 짜면 N+1이 여전히 발생한다.

## 해결 방법 고민

처음엔 여러 방법을 고려했다.

### 방법 1: IN 절로 한 번에 조회

```sql
SELECT * FROM order_items WHERE order_id IN (1, 2, 3, ..., 100);
```

모든 주문 ID를 IN 절에 넣어서 한 번에 조회하는 방식이다.

**장점:**
- 2번의 쿼리로 해결 (orders 1번 + order_items 1번)
- 코드가 명확하고 이해하기 쉬움
- 중복 데이터 없음

**단점:**
- 네트워크 왕복 2번
- IN 절 크기 제한 (MySQL은 보통 1000개까지 권장)

### 방법 2: JOIN으로 한 번에 조회

```sql
SELECT o.*, oi.*
FROM orders o
INNER JOIN order_items oi ON oi.order_id = o.id
WHERE o.user_id = 1;
```

JOIN으로 orders와 order_items를 한 번에 가져오는 방식이다.

**장점:**
- 1번의 쿼리로 해결
- 네트워크 왕복 최소화
- IN 절 크기 제한 없음

**단점:**
- Order가 중복됨 (주문당 아이템 개수만큼)
- 애플리케이션에서 그룹핑 필요

### 선택: IN 절 방식

일단 IN 절 방식을 선택했다. 이유는:

1. 코드가 더 명확하고 유지보수하기 쉬움
2. 주문이 수천 개를 넘지 않을 것 같음
3. JOIN보다 구현이 간단함

나중에 성능이 더 중요해지면 JOIN으로 바꿀 수 있다.

## 구현

### 1단계: Repository에 메서드 추가

먼저 여러 주문의 아이템을 한 번에 조회하는 메서드를 추가했다.

```java
// OrderItemRepository.java
public interface OrderItemRepository {
    List<OrderItem> findByOrderId(Long orderId);

    // 추가: IN 절로 여러 주문의 아이템을 한 번에 조회
    List<OrderItem> findByOrderIdIn(List<Long> orderIds);
}
```

Spring Data JPA가 메서드 이름을 보고 자동으로 쿼리를 생성해준다.

```sql
SELECT * FROM order_items WHERE order_id IN (?, ?, ..., ?);
```

### 2단계: Service 로직 개선

Service에서 IN 절을 사용하도록 바꿨다.

```java
public List<OrderHistoryResponse> getOrderHistory(Long userId) {
    // 1. 사용자의 모든 주문 조회
    List<Order> orders = orderRepository.findByUserId(userId);
    if (orders.isEmpty()) {
        return List.of();
    }

    // 2. 모든 주문의 ID 추출
    List<Long> orderIds = orders.stream()
            .map(Order::getId)
            .toList();

    // 3. IN 절로 모든 주문 아이템을 한 번에 조회
    List<OrderItem> allOrderItems = orderItemRepository.findByOrderIdIn(orderIds);

    // 4. 메모리에서 orderId 기준으로 그룹핑
    Map<Long, List<OrderItem>> orderItemsMap = allOrderItems.stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderId));

    // 5. 각 주문에 해당하는 아이템을 매핑
    return orders.stream()
            .map(order -> {
                List<OrderItem> items = orderItemsMap.getOrDefault(order.getId(), List.of());
                return OrderHistoryResponse.from(order, items);
            })
            .toList();
}
```

핵심은 3번 단계다. 반복문 안이 아니라 **반복문 밖에서** 데이터베이스를 호출한다.

### 동작 흐름

```
1. orders 조회        → DB 1번 왕복
2. order_items 조회    → DB 1번 왕복
3. 메모리에서 그룹핑    → 메모리 작업 (빠름)
4. 매핑                → 메모리 작업 (빠름)
```

총 2번의 데이터베이스 왕복으로 끝난다. 주문이 10개든 100개든 1000개든 항상 2번이다.

## 개선 결과

### 쿼리 수 비교

| 주문 수 | 개선 전 쿼리 | 개선 후 쿼리 | 감소율 |
|---------|-------------|-------------|--------|
| 10개 | 11번 | 2번 | 82% |
| 100개 | 101번 | 2번 | 98% |
| 1,000개 | 1,001번 | 2번 | 99.8% |

주문 개수가 늘어날수록 효과가 극적이다.

### 예상 성능 (네트워크 레이턴시 5ms 가정)

| 주문 수 | 개선 전 | 개선 후 | 개선 효과 |
|---------|---------|---------|-----------|
| 10개 | 55ms | 10ms | 5.5배 |
| 100개 | 505ms | 10ms | **50배** |
| 1,000개 | 5,005ms | 10ms | **500배** |

주문이 많을수록 차이가 엄청나다. 100개 주문 조회가 500ms에서 10ms로 줄어든다.

## 배운 것들

### 반복문 안의 데이터베이스 호출은 위험하다

이게 N+1의 가장 전형적인 패턴이다.

```java
// 나쁜 예
items.forEach(item -> {
    Entity entity = repository.findById(item.getId());  // N번 호출
});

// 좋은 예
List<Long> ids = items.stream().map(Item::getId).toList();
List<Entity> entities = repository.findByIdIn(ids);  // 1번 호출
```

### 메모리 그룹핑이 데이터베이스 왕복보다 빠르다

데이터를 한 번에 가져와서 메모리에서 그룹핑하는 게 효율적이다.

이유는:
- 메모리 작업은 나노초 단위
- 데이터베이스 왕복은 밀리초 단위
- 1000배 이상 차이

주문 아이템 같은 작은 데이터는 메모리에서 처리해도 부담이 없다.

### 간접 참조가 만능은 아니다

간접 참조는 JPA Lazy Loading N+1을 예방하지만, Service 레벨의 N+1은 막지 못한다. 코드를 잘 짜야 한다.

## 다른 방법도 있다

IN 절 외에도 N+1을 해결하는 방법이 있다.

### JOIN 방식

```sql
SELECT o.*, oi.*
FROM orders o
INNER JOIN order_items oi ON oi.order_id = o.id
WHERE o.user_id = ?;
```

**장점:**
- 쿼리 1번으로 해결 (IN은 2번)
- 네트워크 왕복 최소화

**단점:**
- Order가 중복됨 (주문당 아이템 개수만큼)
- 구현이 복잡함

주문당 아이템이 적다면 JOIN이 더 나을 수 있다. 하지만 지금은 IN 방식이 코드가 명확하고 유지보수하기 쉬워서 선택했다.

### Batch Fetch Size (Hibernate)

Hibernate 설정으로 자동 최적화할 수도 있다.

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

하지만 이건 직접 참조를 쓸 때만 효과가 있다. 간접 참조에서는 직접 코드로 해결해야 한다.

## 주의할 점

### IN 절 크기 제한

IN 절에 너무 많은 값을 넣으면 문제가 될 수 있다.

- MySQL: 보통 1000개까지 권장
- Oracle: 1000개 제한

주문이 1000개를 넘으면 배치로 나눠서 조회해야 한다.

```java
// 1000개씩 나눠서 조회
List<OrderItem> allItems = new ArrayList<>();
for (int i = 0; i < orderIds.size(); i += 1000) {
    List<Long> batch = orderIds.subList(i, Math.min(i + 1000, orderIds.size()));
    allItems.addAll(orderItemRepository.findByOrderIdIn(batch));
}
```

다행히 일반 사용자가 주문 1000개를 넘기는 경우는 드물다. 만약 그런 상황이 생기면 페이지네이션을 고려하는 게 맞다.

## 정리

N+1 문제를 발견하고 해결하면서 배운 점:

1. **반복문 안의 데이터베이스 호출은 위험하다**
   - 데이터가 늘어날수록 선형으로 느려짐
   - 101번, 1001번, 10001번...

2. **IN 절로 한 번에 조회하면 해결된다**
   - 항상 2번의 쿼리
   - 주문 개수와 무관

3. **메모리 그룹핑이 데이터베이스 왕복보다 빠르다**
   - 작은 데이터는 메모리에서 처리
   - 1000배 이상 빠름

4. **간접 참조도 N+1을 완벽히 막지 못한다**
   - Service 코드를 잘 짜야 함
   - 의식적으로 N+1을 피해야 함

간단한 코드 변경으로 50~500배 성능 개선을 얻었다. N+1은 찾기도 쉽고 고치기도 쉽다. 하지만 놓치면 성능에 큰 영향을 준다.
