# E-Commerce Server

항해플러스 백엔드 이커머스 프로젝트

## 프로젝트 개요

이커머스 시스템의 핵심 기능인 상품 관리, 주문/결제, 쿠폰, 포인트 시스템을 구현한 서버 애플리케이션입니다.

## 기술 스택

- Java 17
- Spring Boot 3.4.1
- Gradle
- JUnit 5

## 주요 기능

- **상품 관리**: 상품 조회, 목록 조회, 인기 상품 조회
- **주문/결제**: 다중 상품 주문, 결제 처리, 주문 내역 조회
- **쿠폰**: 선착순 쿠폰 발급, 쿠폰 조회, 쿠폰 사용
- **포인트**: 포인트 조회, 충전, 이력 조회
- **데이터 연동**: 외부 데이터 플랫폼 전송 (Outbox 패턴)

## 아키텍처

레이어드 아키텍처를 기반으로 도메인 중심 설계를 적용했습니다.

```
├── interfaces      # API Layer (Controller, DTO)
├── application     # Application Layer (Service, UseCase)
├── domain          # Domain Layer (Entity, Repository Interface)
└── infrastructure  # Infrastructure Layer (Repository Implementation, External API)
```

## 동시성 제어

### 개요

이커머스 시스템에서 재고 차감과 쿠폰 발급은 동시성 제어가 필수적인 기능입니다.
여러 사용자가 동시에 같은 상품을 구매하거나 같은 쿠폰을 발급받으려 할 때, 데이터 정합성을 보장하기 위해 적절한 동시성 제어 메커니즘이 필요합니다.

### 구현 방식

#### 1. ReentrantLock 기반 동시성 제어

**적용 대상:**
- 상품 재고 차감 (`ProductRepository`)
- 쿠폰 발급 수량 관리 (`CouponRepository`)

**핵심 패턴: executeWithLock**

```java
public interface ProductRepository {
    <R> R executeWithLock(Long productId, Function<Product, R> operation);
}
```

이 패턴의 장점:
- Read → Modify → Save 전체 구간을 락으로 보호
- 콜백 함수를 통해 비즈니스 로직을 락 내부에서 실행
- Lost Update 문제 방지

**구현 예시:**

```java
@Override
public <R> R executeWithLock(Long productId, Function<Product, R> operation) {
    ReentrantLock lock = locks.computeIfAbsent(productId, k -> new ReentrantLock(true));

    boolean acquired = false;
    try {
        acquired = lock.tryLock(5, TimeUnit.SECONDS);
        if (!acquired) {
            throw new IllegalStateException("상품 락 획득에 실패했습니다.");
        }

        Product product = store.get(productId);
        R result = operation.apply(product);  // 비즈니스 로직 실행
        store.put(productId, product);        // 변경사항 저장

        return result;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("상품 처리 중 오류가 발생했습니다", e);
    } finally {
        if (acquired) {
            lock.unlock();
        }
    }
}
```

#### 2. Fair Lock (공정 락)

```java
private final ReentrantLock lock = new ReentrantLock(true); // Fair Lock
```

**선택 이유:**
- FIFO(First-In-First-Out) 순서로 락 획득 보장
- 선착순 쿠폰 발급과 같은 요구사항에 적합
- 스레드 기아(Starvation) 방지

**트레이드오프:**
- Non-fair 락 대비 약간의 성능 저하
- 하지만 공정성이 중요한 이커머스 도메인에서는 적절한 선택

#### 3. Timeout 메커니즘

```java
boolean acquired = lock.tryLock(5, TimeUnit.SECONDS);
if (!acquired) {
    throw new IllegalStateException("상품 락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.");
}
```

**선택 이유:**
- 데드락(Deadlock) 방지
- 무한 대기로 인한 스레드 고갈 방지
- 사용자에게 명확한 피드백 제공

**타임아웃 설정 (5초):**
- 일반적인 트랜잭션 처리 시간(1-2초) 대비 충분한 여유
- 사용자가 기다릴 수 있는 합리적인 시간

### 동시성 제어 검증

#### 통합 테스트

**재고 차감 테스트:**
- 50명이 재고 10개 상품 구매 → 정확히 10명만 성공
- 100명이 재고 1개 상품 구매 → 정확히 1명만 성공

**쿠폰 발급 테스트:**
- 100명이 50개 쿠폰 발급 시도 → 정확히 50명만 성공
- 50명이 1개 쿠폰 발급 시도 → 정확히 1명만 성공

**테스트 도구:**
- `ExecutorService`: 멀티스레드 환경 시뮬레이션
- `CountDownLatch`: 동시 시작 보장
- `AtomicInteger`: 스레드 안전한 카운터

**테스트 코드 예시:**

```java
@Test
@DisplayName("50명이 동시에 재고 10개 상품을 1개씩 구매하면 10명만 성공한다")
void orderProduct_Concurrency_10OutOf50() throws InterruptedException {
    Product product = new Product(1L, "인기상품", "한정수량", 10000, 10, "전자");
    productRepository.save(product);

    int threadCount = 50;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    for (int i = 1; i <= threadCount; i++) {
        final long userId = i;
        executorService.submit(() -> {
            try {
                startLatch.await();  // 모든 스레드 동시 시작
                orderService.createOrder(request);
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);
    executorService.shutdown();

    assertThat(successCount.get()).isEqualTo(10);
    assertThat(failCount.get()).isEqualTo(40);
    assertThat(product.getStockQuantity()).isEqualTo(0);
}
```

## 프로젝트 구조

```
src/
├── main/
│   └── java/com/ecommerce/
│       ├── domain/           # 도메인 엔티티 및 리포지토리 인터페이스
│       │   ├── entity/
│       │   └── repository/
│       ├── application/       # 애플리케이션 서비스 및 DTO
│       │   ├── service/
│       │   └── dto/
│       ├── infrastructure/    # 리포지토리 구현체 및 외부 연동
│       │   ├── repository/
│       │   └── external/
│       └── interfaces/        # 컨트롤러 및 API
│           └── controller/
└── test/
    └── java/com/ecommerce/
        ├── domain/entity/     # 도메인 엔티티 단위 테스트
        ├── application/       # 서비스 및 통합 테스트
        └── interfaces/        # 컨트롤러 테스트
```

## 실행 방법

```bash
# 빌드
./gradlew build

# 테스트 실행
./gradlew test

# 애플리케이션 실행
./gradlew bootRun
```

## 문서

프로젝트 관련 상세 문서는 `docs/` 디렉토리에 있습니다:

- [요구사항 정의서](docs/01_requirements.md)
- [ERD 설계](docs/02_erd.md)
- [시퀀스 다이어그램](docs/03_sequence_diagram.md)
- [동시성 제어](docs/04_concurrency_control.md)
