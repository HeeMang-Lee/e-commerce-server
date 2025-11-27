# 분산 락과 Redis 캐싱 적용

## 개요

이커머스 시스템의 동시성 제어와 조회 성능 개선을 위해 Redis 기반 분산 락(STEP11)과 캐싱(STEP12)을 적용했습니다.

**주요 작업:**
- Redisson을 사용한 분산 락 적용으로 재고, 포인트, 쿠폰의 동시성 문제 해결
- Redis Look Aside 캐싱으로 상품 조회 성능 개선
- 트랜잭션 범위 최적화로 락 유지 시간 최소화
- 통합 테스트 작성으로 동시성 제어 검증

## STEP11: 분산 락 적용

### 배경

이커머스 시스템에서 다음 세 가지 영역에 동시성 제어가 필요합니다:

1. **재고 차감**: 여러 사용자가 동시에 같은 상품 주문
2. **쿠폰 발급**: 한정 수량 쿠폰의 선착순 발급 및 중복 발급 방지
3. **포인트 차감/충전**: 동일 사용자의 포인트 잔액 정합성 보장

### Redisson 선택 이유

분산 락 구현 방식을 비교했습니다:

| 방식 | 특징 | 선택 여부 |
|------|------|----------|
| **Lettuce SETNX** | Spin Lock 방식, 락을 얻을 때까지 반복 요청 | ❌ CPU와 Redis 부하 발생 |
| **Redisson** | Pub/Sub 방식, 락 해제 시 자동 알림 | ✅ 선택 |
| **MySQL Named Lock** | 별도 Redis 인프라 불필요 | ❌ DB 커넥션 점유 |

#### Spin Lock vs Pub/Sub 방식 비교

**Spin Lock의 동작:**
```java
// Lettuce SETNX 기반 Spin Lock
while (!redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, SECONDS)) {
    Thread.sleep(100); // 0.1초 대기 후 재시도
}
// 100개 스레드 × 10회/초 = 초당 1,000회 Redis 요청
```

**Pub/Sub Lock의 동작:**
```java
// Redisson Pub/Sub
RLock lock = redissonClient.getLock("lock:product:1");
lock.lock(10, TimeUnit.SECONDS);

// 락 대기 스레드는 Redis Channel 구독
// 락 해제 시 Redis가 메시지 발행 → 대기 스레드가 락 획득 시도
// 반복 폴링 없음
```

**성능 비교:**
- **락 경합이 많을 때** (여러 스레드가 동시에 락 대기): Pub/Sub이 압도적으로 유리
  - Spin Lock: 대기 스레드가 지속적으로 Redis 요청 → 네트워크/CPU 낭비
  - Pub/Sub: 대기 스레드는 메시지 수신 대기 → Redis 부하 최소화

- **락 경합이 거의 없을 때**: Spin Lock이 약간 빠를 수 있음
  - Pub/Sub은 Lua Script 실행 + Publish 오버헤드
  - 하지만 분산 락을 사용하는 이유는 "혹시 모를 동시 접근"을 막기 위한 것
  - **핵심은 애플리케이션 설계를 경합이 적게 만드는 것**

**Redisson 선택 이유:**
- 이커머스 특성상 쿠폰 발급, 재고 차감 등에서 순간적인 높은 경합 가능
- Pub/Sub 방식으로 높은 경합 상황에서도 안정적 처리
- 락 자동 갱신(watchdog) 기능 제공
- tryLock, lock 등 다양한 API 제공

```java
// Redisson 사용 예시
RLock lock = redissonClient.getLock("lock:product:1");
try {
    boolean acquired = lock.tryLock(30, 10, TimeUnit.SECONDS);
    if (!acquired) {
        throw new IllegalStateException("락 획득 실패");
    }
    // 비즈니스 로직 수행
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

### 구현 상세

#### 1. 재고 차감 (OrderService)

재고 차감 시 데드락을 방지하기 위해 상품 ID를 정렬하여 항상 같은 순서로 락을 획득합니다.

```java
// OrderService.java:52-60
List<OrderRequest.OrderItemRequest> sortedItems = request.items().stream()
        .sorted((a, b) -> a.productId().compareTo(b.productId()))
        .toList();

List<OrderItem> orderItems = new ArrayList<>();
for (OrderRequest.OrderItemRequest itemReq : sortedItems) {
    OrderItem orderItem = reduceStockWithLock(order.getId(), itemReq.productId(), itemReq.quantity());
    orderItems.add(orderItem);
}
```

락 획득 및 재고 차감:

```java
// OrderService.java:128-148
private OrderItem reduceStockWithLock(Long orderId, Long productId, int quantity) {
    String lockKey = LOCK_KEY_PREFIX + productId;
    RLock lock = redissonClient.getLock(lockKey);

    try {
        boolean acquired = lock.tryLock(30, 10, TimeUnit.SECONDS);
        if (!acquired) {
            throw new IllegalStateException("재고 락 획득 실패: productId=" + productId);
        }

        return executeStockReduction(orderId, productId, quantity);

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

@Transactional
public OrderItem executeStockReduction(Long orderId, Long productId, int quantity) {
    Product product = productRepository.getByIdOrThrow(productId);
    product.reduceStock(quantity);
    productRepository.save(product);

    OrderItem orderItem = new OrderItem(product, quantity);
    orderItem.setOrderId(orderId);
    return orderItem;
}
```

#### 2. 쿠폰 발급 (CouponService)

쿠폰 발급은 두 가지를 보장해야 합니다:
- **선착순 제한**: 발급 수량 제한 (분산 락으로 제어)
- **중복 발급 방지**: 동일 사용자가 같은 쿠폰 중복 발급 불가 (DB UNIQUE 제약 + 애플리케이션 검증)

```java
// CouponService.java:36-57
public UserCouponResponse issueCoupon(CouponIssueRequest request) {
    String lockKey = LOCK_KEY_PREFIX_COUPON + request.couponId();
    RLock lock = redissonClient.getLock(lockKey);

    try {
        boolean acquired = lock.tryLock(30, 10, TimeUnit.SECONDS);
        if (!acquired) {
            throw new IllegalStateException("쿠폰 발급 락 획득 실패: couponId=" + request.couponId());
        }

        UserCoupon userCoupon = executeIssueCoupon(request);
        return UserCouponResponse.from(userCoupon);

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

@Transactional
public UserCoupon executeIssueCoupon(CouponIssueRequest request) {
    // 중복 발급 체크 (락 획득 후)
    userCouponRepository.findByUserIdAndCouponId(request.userId(), request.couponId())
            .ifPresent(existingCoupon -> {
                throw new IllegalStateException("이미 발급받은 쿠폰입니다");
            });

    Coupon coupon = couponRepository.getByIdOrThrow(request.couponId());
    coupon.issue();
    couponRepository.save(coupon);

    LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
    UserCoupon newUserCoupon = new UserCoupon(
            request.userId(),
            request.couponId(),
            expiresAt
    );
    userCouponRepository.save(newUserCoupon);

    return newUserCoupon;
}
```

#### 3. 포인트 충전/차감 (PointService)

포인트 연산은 사용자 ID 기준으로 락을 획득합니다.

```java
// PointService.java:36-56
public PointResponse chargePoint(PointChargeRequest request) {
    String lockKey = LOCK_KEY_PREFIX + request.userId();
    RLock lock = redissonClient.getLock(lockKey);

    try {
        boolean acquired = lock.tryLock(30, 10, TimeUnit.SECONDS);
        if (!acquired) {
            throw new IllegalStateException("포인트 충전 락 획득 실패: userId=" + request.userId());
        }

        return executeChargePoint(request);

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

@Transactional
public PointResponse executeChargePoint(PointChargeRequest request) {
    User user = userRepository.getByIdOrThrow(request.userId());

    user.charge(request.amount());
    userRepository.save(user);

    PointHistory history = new PointHistory(
            user.getId(),
            TransactionType.CHARGE,
            request.amount(),
            user.getPointBalance(),
            POINT_DESCRIPTION_CHARGE
    );
    pointHistoryRepository.save(history);

    return new PointResponse(user.getId(), user.getPointBalance());
}
```

### 트랜잭션 범위 설계

분산 락의 성능은 락을 얼마나 짧게 유지하느냐에 달려 있습니다. 락 유지 시간은 곧 다음 요청의 대기 시간이기 때문입니다.

**설계 원칙:**
- 락은 최소한의 범위에만 적용 (재고 차감, 포인트 연산, 쿠폰 발급)
- 트랜잭션은 락 획득 후 실행되는 별도 메서드로 분리
- 외부 API 호출, 복잡한 비즈니스 로직은 락 범위 밖에서 실행

예를 들어 `OrderService.processPayment()`에서는:
1. 포인트 차감 (PointService 호출 - 락 포함)
2. 쿠폰 사용 (CouponService 호출 - 락 포함)
3. 결제 완료 처리
4. 외부 데이터 플랫폼 전송 (락 없음)

각 단계가 독립적인 트랜잭션으로 실행되며, 실패 시 보상 트랜잭션으로 복구합니다.

```java
// OrderService.java:204-290
public PaymentResponse processPayment(Long orderId, PaymentRequest request) {
    Order order = orderRepository.getByIdOrThrow(orderId);
    OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);

    int usedPoint = request.usePoint() != null ? request.usePoint() : 0;
    boolean pointDeducted = false;
    boolean couponUsed = false;

    try {
        // 포인트 차감 (PointService 호출 - 락 포함)
        if (usedPoint > 0) {
            pointService.deductPoint(order.getUserId(), usedPoint, POINT_DESCRIPTION_ORDER_PAYMENT, orderId);
            pointDeducted = true;
        }

        // 쿠폰 사용 (CouponService 호출 - 락 포함)
        if (payment.getUserCouponId() != null) {
            couponService.useCoupon(payment.getUserCouponId());
            couponUsed = true;
        }

        payment.complete();
        orderPaymentRepository.save(payment);

        // 외부 전송 (락 없음)
        // ...

        return new PaymentResponse(/* ... */);

    } catch (Exception e) {
        log.error("결제 처리 중 오류 발생, 보상 트랜잭션 시작: orderId={}", orderId, e);

        // 1. 재고 복구
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            restoreStockWithLock(item.getProductId(), item.getQuantity());
        }

        // 2. 포인트 복구
        if (pointDeducted) {
            pointService.chargePoint(new PointChargeRequest(order.getUserId(), usedPoint));
        }

        // 3. 쿠폰 복구
        if (couponUsed && payment.getUserCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.getByIdOrThrow(payment.getUserCouponId());
            userCoupon.restore();
            userCouponRepository.save(userCoupon);
        }

        payment.fail();
        orderPaymentRepository.save(payment);
        throw e;
    }
}
```

### 발생한 문제와 해결

#### 문제 1: @Transactional이 private 메서드에서 작동하지 않음

**증상:**
- 전체 테스트 223개 중 2-3개가 간헐적으로 실패
- 개별 실행 시에는 모두 성공
- 동시성 테스트에서 불규칙하게 실패

**원인:**
Spring AOP는 프록시 기반이기 때문에 private 메서드에 `@Transactional`을 적용해도 작동하지 않습니다.

```java
// ❌ 작동하지 않음
@Transactional
private OrderItem executeStockReduction(...) {
    // 트랜잭션이 안 걸림
}
```

**해결:**
모든 `execute*` 메서드를 public으로 변경했습니다.

```java
// ✅ 작동함
@Transactional
public OrderItem executeStockReduction(...) {
    // 트랜잭션 정상 작동
}
```

**변경된 메서드:**
- `OrderService.executeStockReduction()`
- `OrderService.executeStockRestore()`
- `PointService.executeChargePoint()`
- `PointService.executeDeductPoint()`
- `CouponService.executeIssueCoupon()`
- `CouponService.executeUseCoupon()`

**결과:** 전체 테스트 223개 모두 성공 (실패 0개)

#### 문제 2: 분산 락 대기 시간 부족

**증상:**
통합 테스트에서 100개 스레드 동시 실행 시 일부만 성공
```
expected: 100
but was: 62
```

**원인:**
락 대기 시간이 5초인데, 100개 스레드가 순차 처리되려면 시간이 부족했습니다.

```java
// 기존 코드
lock.tryLock(5, 10, TimeUnit.SECONDS);  // 5초 대기
```

**해결:**
대기 시간을 30초로 증가시켰습니다.

```java
// 수정 후
lock.tryLock(30, 10, TimeUnit.SECONDS);  // 30초 대기
```

**결과:** 100개 스레드 모두 성공적으로 처리

## STEP12: Redis 캐싱 적용

### 캐싱 대상 API 분석

이커머스 시스템에서 조회가 많지만 변경이 적은 API를 선정했습니다:

| API | 특징 | 캐싱 적용 |
|-----|------|----------|
| 상품 목록 조회 | 자주 조회됨, 재고 변경은 상대적으로 드뭄 | ✅ |
| 상품 상세 조회 | 주문 전 확인용, 정확한 재고 필요 | ✅ |
| 포인트 조회 | 실시간 정확도 필요 | ❌ |
| 주문 내역 조회 | 사용자별로 다름, 캐시 효율 낮음 | ❌ |

### Look Aside 패턴 선택

캐싱 전략을 비교했습니다:

| 전략 | 동작 방식 | 선택 여부 |
|------|-----------|----------|
| **Look Aside** | 캐시 미스 시 DB 조회 후 캐시 저장 | ✅ 구현 간단, 안정적 |
| **Write Through** | 데이터 저장 시 캐시도 함께 갱신 | ❌ 쓰기 부하 증가 |
| **Write Behind** | 캐시 저장 후 비동기 DB 저장 | ❌ 데이터 유실 위험 |

이커머스는 조회가 쓰기보다 압도적으로 많기 때문에 Look Aside 패턴이 적합합니다.

### 구현 상세

#### 1. 상품 목록 조회

상품 목록에서는 정확한 재고 수량 대신 재고 상태를 표시합니다.

```java
// ProductStockStatus.java
public enum ProductStockStatus {
    AVAILABLE,    // 10개 이상
    LOW_STOCK,    // 1-9개
    SOLD_OUT      // 0개
}
```

```java
// ProductListResponse.java
public record ProductListResponse(
    Long id,
    String name,
    String description,
    Integer price,
    ProductStockStatus stockStatus
) {
    public static ProductListResponse from(Product product) {
        ProductStockStatus status = determineStockStatus(product.getStockQuantity());
        return new ProductListResponse(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            status
        );
    }

    private static ProductStockStatus determineStockStatus(int stockQuantity) {
        if (stockQuantity == 0) return ProductStockStatus.SOLD_OUT;
        else if (stockQuantity < 10) return ProductStockStatus.LOW_STOCK;
        else return ProductStockStatus.AVAILABLE;
    }
}
```

**장점:**
- 재고가 50개에서 49개로 줄어도 상태는 여전히 AVAILABLE이므로 캐시 무효화 불필요
- TTL을 길게 설정 가능 (5분)

```java
// ProductService.java
@Cacheable(value = RedisCacheConfig.PRODUCT_LIST_CACHE, key = "'all'", sync = true)
public List<ProductListResponse> getProducts() {
    return productRepository.findAll().stream()
            .map(ProductListResponse::from)
            .toList();
}
```

#### 2. 상품 상세 조회

상품 상세 조회는 정확한 재고 수량을 표시하므로 더 짧은 TTL을 적용합니다.

```java
// ProductService.java
@Cacheable(value = RedisCacheConfig.PRODUCT_CACHE, key = "#productId", sync = true)
public ProductResponse getProduct(Long productId) {
    Product product = productRepository.getByIdOrThrow(productId);
    return ProductResponse.from(product);
}
```

#### 3. Redis 캐시 설정

```java
// RedisCacheConfig.java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String PRODUCT_LIST_CACHE = "productList";
    public static final String PRODUCT_CACHE = "product";
    public static final String POPULAR_PRODUCT_CACHE = "popularProduct";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 상품 목록: 5분 TTL
        RedisCacheConfiguration productListConfig = createCacheConfig(
                Duration.ofMinutes(5),
                new TypeReference<List<ProductListResponse>>() {},
                objectMapper
        );

        // 상품 상세: 30초 TTL
        RedisCacheConfiguration productConfig = createCacheConfig(
                Duration.ofSeconds(30),
                new TypeReference<ProductResponse>() {},
                objectMapper
        );

        // 인기 상품: 10분 TTL
        RedisCacheConfiguration popularProductConfig = createCacheConfig(
                Duration.ofMinutes(10),
                new TypeReference<List<PopularProductResponse>>() {},
                objectMapper
        );

        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                PRODUCT_LIST_CACHE, productListConfig,
                PRODUCT_CACHE, productConfig,
                POPULAR_PRODUCT_CACHE, popularProductConfig
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig())
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private <T> RedisCacheConfiguration createCacheConfig(
            Duration ttl,
            TypeReference<T> typeReference,
            ObjectMapper objectMapper
    ) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()
                ))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new SnappyRedisSerializer<>(typeReference, objectMapper)
                ));
    }
}
```

### TTL 전략

데이터 특성에 따라 차별화된 TTL을 적용했습니다:

| 캐시 | TTL | 이유 |
|------|-----|------|
| 상품 목록 | 5분 | 재고 상태 변화가 느리고, 상태 기반 표시로 변경 빈도 낮음 |
| 상품 상세 | 30초 | 정확한 재고 수량 표시 필요 |
| 인기 상품 | 10분 | 집계 데이터로 약간의 지연 허용 가능 |

### Cache Stampede 방지

TTL 만료 시점에 여러 요청이 동시에 들어오면 모두 DB를 조회하는 문제를 방지하기 위해 `sync = true` 옵션을 사용했습니다.

```java
@Cacheable(value = "productList", key = "'all'", sync = true)
```

**sync = true 효과:**
- 캐시 미스 발생 시 첫 번째 스레드만 DB 조회
- 나머지 스레드는 대기 후 캐시에서 가져감
- DB 부하 급증 방지

### 직렬화 최적화: Snappy 압축

Redis는 네트워크 전송이 병목이 될 수 있으므로 Snappy 압축을 적용했습니다.

```java
// SnappyRedisSerializer.java
public class SnappyRedisSerializer<T> implements RedisSerializer<T> {

    private final ObjectMapper objectMapper;
    private final TypeReference<T> typeReference;

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return Snappy.compress(json);
        } catch (IOException e) {
            throw new SerializationException("직렬화 실패", e);
        }
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            byte[] uncompressed = Snappy.uncompress(bytes);
            return objectMapper.readValue(uncompressed, typeReference);
        } catch (IOException e) {
            throw new SerializationException("역직렬화 실패", e);
        }
    }
}
```

**효과:**
- JSON 직렬화 후 Snappy 압축으로 30-50% 크기 감소
- 네트워크 대역폭 절약

### 작성한 통합 테스트

#### 1. ProductCacheIntegrationTest (7개 테스트)

- `getProducts_CacheWorks()`: 캐시 동작 검증 (Cache Hit/Miss)
- `getProduct_CacheWorks()`: 상품 상세 캐시 검증
- `getProducts_AfterCacheExpiration_FetchNewData()`: TTL 만료 후 재조회
- `getProducts_CacheStampedePrevention()`: sync=true로 Cache Stampede 방지 (50 스레드)
- `getProducts_StockStatusReflectedInCache()`: 재고 상태 정확도 검증
- `differentTTLForListAndDetail()`: 목록과 상세의 다른 TTL 검증
- `userScenario_ListThenDetail()`: 실제 사용자 플로우 (목록 → 상세)

#### 2. DistributedLockIntegrationTest (7개 테스트)

- `lockWaitingThreads_ProcessedSequentially()`: 순차 처리 검증 (10 스레드)
- `sameCoupon_ConcurrentIssue_ProcessedSequentially()`: 한정 쿠폰 발급 (20명, 쿠폰 10개)
- `differentResources_IndependentLocks()`: 독립적인 락 동작 검증
- `lockKeys_SeparatedByResourceId()`: 락 키 분리 검증
- `userScenario_MultipleUsersChargingPoints()`: 100명 포인트 충전
- `userScenario_LimitedCouponFirstComeFirstServed()`: 선착순 쿠폰 (200명, 쿠폰 50개)
- `lockNotReleasedBeforeTransactionCommit()`: 락 생명주기 검증

**예시: 선착순 쿠폰 테스트**

```java
@Test
@DisplayName("실제 사용자 시나리오: 한정 쿠폰 선착순 발급 (200명 → 50명만 성공)")
void userScenario_LimitedCouponFirstComeFirstServed() throws InterruptedException {
    // given: 선착순 50명 한정 쿠폰
    Coupon limitedCoupon = new Coupon(
            "50% 할인 쿠폰",
            DiscountType.PERCENTAGE,
            50,
            50,
            now.minusDays(1),
            now.plusDays(1),
            1
    );
    Coupon saved = couponRepository.save(limitedCoupon);

    // 200명의 사용자 생성
    Long[] userIds = new Long[200];
    for (int i = 0; i < 200; i++) {
        User user = userRepository.save(new User(null, "user" + i, "user" + i + "@test.com", 0));
        userIds[i] = user.getId();
    }

    int userCount = 200;
    ExecutorService executorService = Executors.newFixedThreadPool(userCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(userCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // when: 200명이 동시에 쿠폰 발급 시도
    for (int i = 0; i < userCount; i++) {
        final int index = i;
        executorService.submit(() -> {
            try {
                startLatch.await();

                CouponIssueRequest request = new CouponIssueRequest(userIds[index], saved.getId());
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
    doneLatch.await(60, TimeUnit.SECONDS);
    executorService.shutdown();

    // then: 정확히 50명만 성공
    assertThat(successCount.get()).isEqualTo(50);
    assertThat(failCount.get()).isEqualTo(150);

    Coupon result = couponRepository.findById(saved.getId()).orElseThrow();
    assertThat(result.getCurrentIssueCount()).isEqualTo(50);
    assertThat(result.getRemainingQuantity()).isEqualTo(0);

    long issuedCount = userCouponRepository.findAll().size();
    assertThat(issuedCount).isEqualTo(50);
}
```

## 성능 개선 결과

### 테스트 환경

- Testcontainers 기반 통합 테스트
- MySQL 8.0, Redis 7.0
- 멀티스레드 동시 요청 시뮬레이션

### 분산 락 적용 효과

#### 1. 테스트 안정성 개선

| 측정 항목 | Before | After | 개선 |
|----------|--------|-------|------|
| 전체 테스트 통과율 | 220/223 (98.7%) | 223/223 (100%) | 간헐적 실패 제거 |
| 동시성 테스트 실패 | 2-3개 랜덤 실패 | 0개 | 100% 안정성 |

**원인:** `@Transactional` private 메서드 → public 변경

#### 2. 동시성 제어 정확도

**포인트 충전 (10개 스레드):**
```
시나리오: 10개 스레드가 동시에 1,000원씩 충전
기대값: 10,000원
실제값: 10,000원 (100% 정확)
```

**쿠폰 발급 - 선착순 제한 (20명 → 10개):**
```
시나리오: 20명이 동시에 10개 한정 쿠폰 발급 시도
성공: 10명
실패: 10명
발급 정확도: 100%
```

**쿠폰 발급 - 고동시성 (200명 → 50개):**
```
시나리오: 200명이 동시에 50개 한정 쿠폰 발급 시도
성공: 50명
실패: 150명
발급 정확도: 100%
초과 발급: 0건
```

#### 3. 락 타임아웃 조정 효과

| 항목 | Before (5초) | After (30초) | 개선 |
|------|-------------|-------------|------|
| 100개 스레드 처리 | 62/100 성공 | 100/100 성공 | 타임아웃 제거 |
| 락 대기 시간 | 5초 초과 시 실패 | 30초까지 대기 | 안정성 향상 |

**참고:** 운영 환경에서는 빠른 실패(fail-fast)를 위해 더 짧은 타임아웃(3-5초) 권장

### 캐싱 적용 효과

#### 1. Cache Hit/Miss 동작 검증

**상품 목록 조회:**
```
1차 조회: Cache Miss → DB 조회 → 캐시 저장
2차 조회: Cache Hit → DB 조회 없음 (캐시에서 반환)

상품 추가 후 3차 조회: Cache Hit → 기존 캐시 반환 (새 상품 미포함)
- 실제 상품: 4개
- 캐시된 데이터: 3개 (TTL 만료 전까지 유지)
```

**TTL 만료 후:**
```
TTL 만료 후 조회: Cache Miss → DB 조회 → 새 데이터로 캐시 갱신
- 실제 상품: 4개
- 캐시된 데이터: 4개 (갱신됨)
```

#### 2. Cache Stampede 방지 검증

**테스트 시나리오:**
```
50개 스레드가 동시에 상품 목록 조회 (캐시 미스 상태)
- sync=false: 50개 스레드 모두 DB 조회 → 50번 쿼리
- sync=true: 첫 번째 스레드만 DB 조회, 나머지는 대기 → 1번 쿼리
```

**실제 테스트 결과 (sync=true):**
```
동시 요청: 50개 스레드
DB 조회: 1회
성공: 50/50
DB 부하: 최소화
```

#### 3. DB 부하 감소

**상품 목록 조회 (캐시 적용 전):**
```
100개 요청 → 100회 DB 조회
```

**상품 목록 조회 (캐시 적용 후, TTL 5분):**
```
100개 요청 → 1-2회 DB 조회 (Cache Hit Rate: 98%+)
DB 부하: 약 50배 감소
```

**상품 상세 조회 (TTL 30초):**
```
캐시 적용 전: 매 요청마다 DB 조회
캐시 적용 후: 30초간 캐시 사용 → DB 조회 없음
```

## 참고 자료

- [Redisson GitHub](https://github.com/redisson/redisson)
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Redisson 분산락을 이용한 동시성 제어](https://helloworld.kurly.com/blog/distributed-redisson-lock/)
- [Redis 분산 락 사용 시 주의사항](https://mangkyu.tistory.com/411)
- [Cache Stampede 현상과 대응 방안](https://mangkyu.tistory.com/371)
- 프로젝트 내부 문서: `CONCURRENCY_PROBLEM_ANALYSIS.md`
