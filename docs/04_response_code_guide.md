# 응답 코드 사용 가이드

## 개요

이 프로젝트는 도메인별로 분류된 커스텀 응답 코드 시스템을 사용합니다. 이를 통해 클라이언트는 HTTP 상태 코드와 함께 더 구체적인 비즈니스 코드를 받아 세밀한 처리를 할 수 있습니다.

## 응답 구조

모든 API 응답은 다음과 같은 구조를 가집니다:

```json
{
  "success": true,
  "code": "PRODUCT_2000",
  "data": { ... },
  "message": "상품 조회에 성공했습니다."
}
```

- `success`: 성공 여부 (boolean)
- `code`: 비즈니스 응답 코드 (String)
- `data`: 실제 응답 데이터 (제네릭)
- `message`: 사용자에게 표시할 메시지 (String)

## 응답 코드 체계

### 코드 구조

```
{도메인}_{숫자}
```

### 도메인별 코드 범위

| 도메인 | 코드 범위 | 설명 |
|--------|----------|------|
| COMMON | 1xxx | 공통 응답 |
| PRODUCT | 2xxx | 상품 관련 |
| ORDER | 3xxx | 주문 관련 |
| COUPON | 4xxx | 쿠폰 관련 |
| POINT | 5xxx | 포인트 관련 |

## 정의된 응답 코드

### 공통 (1xxx)

| 코드 | HTTP 상태 | 메시지 | 사용 시점 |
|------|-----------|--------|----------|
| COMMON_1000 | 200 OK | 요청이 성공적으로 처리되었습니다. | 일반적인 성공 응답 |
| COMMON_1001 | 201 Created | 리소스가 성공적으로 생성되었습니다. | 리소스 생성 시 |
| COMMON_1400 | 400 Bad Request | 잘못된 요청입니다. | 입력 값 검증 실패 |
| COMMON_1401 | 401 Unauthorized | 인증이 필요합니다. | 인증 실패 |
| COMMON_1403 | 403 Forbidden | 접근 권한이 없습니다. | 권한 부족 |
| COMMON_1404 | 404 Not Found | 요청한 리소스를 찾을 수 없습니다. | 리소스 없음 |
| COMMON_1500 | 500 Internal Server Error | 서버 내부 오류가 발생했습니다. | 예상치 못한 에러 |

### 상품 (2xxx)

| 코드 | HTTP 상태 | 메시지 | 사용 시점 |
|------|-----------|--------|----------|
| PRODUCT_2000 | 200 OK | 상품 조회에 성공했습니다. | 상품 조회 성공 |
| PRODUCT_2001 | 404 Not Found | 상품을 찾을 수 없습니다. | 상품이 존재하지 않음 |
| PRODUCT_2002 | 400 Bad Request | 상품 재고가 부족합니다. | 재고 부족 |
| PRODUCT_2003 | 400 Bad Request | 판매 중지된 상품입니다. | 판매 불가 상품 |

### 주문 (3xxx)

| 코드 | HTTP 상태 | 메시지 | 사용 시점 |
|------|-----------|--------|----------|
| ORDER_3000 | 200 OK | 주문 조회에 성공했습니다. | 주문 조회 성공 |
| ORDER_3001 | 201 Created | 주문이 생성되었습니다. | 주문 생성 성공 |
| ORDER_3002 | 404 Not Found | 주문을 찾을 수 없습니다. | 주문이 존재하지 않음 |
| ORDER_3003 | 400 Bad Request | 주문 상태가 올바르지 않습니다. | 잘못된 주문 상태 |
| ORDER_3004 | 400 Bad Request | 이미 결제된 주문입니다. | 중복 결제 시도 |
| ORDER_3005 | 400 Bad Request | 결제 처리에 실패했습니다. | 결제 실패 |
| ORDER_3006 | 200 OK | 결제가 완료되었습니다. | 결제 성공 |

### 쿠폰 (4xxx)

| 코드 | HTTP 상태 | 메시지 | 사용 시점 |
|------|-----------|--------|----------|
| COUPON_4000 | 200 OK | 쿠폰 조회에 성공했습니다. | 쿠폰 조회 성공 |
| COUPON_4001 | 201 Created | 쿠폰이 발급되었습니다. | 쿠폰 발급 성공 |
| COUPON_4002 | 404 Not Found | 쿠폰을 찾을 수 없습니다. | 쿠폰이 존재하지 않음 |
| COUPON_4003 | 400 Bad Request | 이미 발급받은 쿠폰입니다. | 중복 발급 시도 |
| COUPON_4004 | 400 Bad Request | 만료된 쿠폰입니다. | 쿠폰 기간 만료 |
| COUPON_4005 | 400 Bad Request | 쿠폰 발급이 마감되었습니다. | 쿠폰 재고 소진 |
| COUPON_4006 | 400 Bad Request | 이미 사용된 쿠폰입니다. | 사용 완료 쿠폰 |

### 포인트 (5xxx)

| 코드 | HTTP 상태 | 메시지 | 사용 시점 |
|------|-----------|--------|----------|
| POINT_5000 | 200 OK | 포인트 조회에 성공했습니다. | 포인트 조회 성공 |
| POINT_5001 | 200 OK | 포인트가 충전되었습니다. | 포인트 충전 성공 |
| POINT_5002 | 400 Bad Request | 포인트가 부족합니다. | 잔액 부족 |
| POINT_5003 | 400 Bad Request | 유효하지 않은 포인트 금액입니다. | 잘못된 금액 |
| POINT_5004 | 400 Bad Request | 포인트 충전 한도를 초과했습니다. | 충전 한도 초과 |

## 사용 방법

### 1. Controller에서 성공 응답 반환

```java
@RestController
public class ProductController implements ProductApi {

    @Override
    public ApiResponse<ProductDto.Response> getProduct(Long productId) {
        ProductDto.Response product = productService.getProduct(productId);

        // ResponseCode를 사용한 응답
        return ApiResponse.of(ResponseCode.PRODUCT_SUCCESS, product);
    }
}
```

### 2. 예외를 사용한 에러 응답

```java
@RestController
public class ProductController implements ProductApi {

    @Override
    public ApiResponse<ProductDto.Response> getProduct(Long productId) {
        ProductDto.Response product = productService.findById(productId)
                .orElseThrow(() -> new BusinessException(ResponseCode.PRODUCT_NOT_FOUND));

        return ApiResponse.of(ResponseCode.PRODUCT_SUCCESS, product);
    }
}
```

### 3. 커스텀 메시지와 함께 응답

```java
return ApiResponse.of(
    ResponseCode.ORDER_CREATED,
    order,
    "주문번호 " + order.getOrderNumber() + "가 생성되었습니다."
);
```

### 4. 에러 발생 시 커스텀 메시지

```java
throw new BusinessException(
    ResponseCode.PRODUCT_OUT_OF_STOCK,
    "상품 재고가 " + stock + "개 남아있습니다."
);
```

## 응답 코드 추가하기

새로운 응답 코드가 필요한 경우 `ResponseCode.java`에 추가합니다:

```java
// src/main/java/com/ecommerce/dto/ResponseCode.java

public enum ResponseCode {
    // 기존 코드들...

    // 새로운 코드 추가
    PRODUCT_2004(HttpStatus.BAD_REQUEST, "PRODUCT_2004", "상품이 일시적으로 품절되었습니다."),
    ORDER_3007(HttpStatus.BAD_REQUEST, "ORDER_3007", "주문 취소 가능 기간이 지났습니다."),

    // ...
}
```

## 클라이언트 활용 예시

### JavaScript/TypeScript

```typescript
const response = await fetch('/api/products/1');
const data = await response.json();

if (data.success) {
    // 성공 처리
    switch (data.code) {
        case 'PRODUCT_2000':
            console.log('상품 조회 성공:', data.data);
            break;
    }
} else {
    // 에러 처리
    switch (data.code) {
        case 'PRODUCT_2001':
            showError('상품을 찾을 수 없습니다.');
            break;
        case 'PRODUCT_2002':
            showError('재고가 부족합니다.');
            break;
        default:
            showError(data.message);
    }
}
```

### Android/Kotlin

```kotlin
when (response.code) {
    "PRODUCT_2000" -> {
        // 상품 조회 성공
        showProduct(response.data)
    }
    "PRODUCT_2001" -> {
        // 상품 없음
        showNotFoundDialog()
    }
    "PRODUCT_2002" -> {
        // 재고 부족
        showOutOfStockDialog()
    }
    else -> {
        showErrorDialog(response.message)
    }
}
```

## 예외 처리 흐름

```
비즈니스 로직 에러 발생
    ↓
BusinessException 발생
    ↓
GlobalExceptionHandler가 catch
    ↓
ApiResponse로 변환
    ↓
HTTP 상태코드 + 응답 코드 반환
```

## 주의사항

1. **일관성 유지**: 같은 종류의 에러는 항상 같은 응답 코드를 사용합니다.
2. **확장 시 범위 준수**: 새로운 코드 추가 시 도메인별 코드 범위를 지킵니다.
3. **메시지 명확성**: 사용자가 이해할 수 있는 명확한 메시지를 작성합니다.
4. **HTTP 상태코드 매칭**: 비즈니스 코드와 HTTP 상태코드가 의미적으로 일치해야 합니다.

## 테스트 예시

응답 코드가 올바르게 반환되는지 확인:

```bash
# 상품 조회 - PRODUCT_2000
curl http://localhost:8081/api/products/1

# 주문 생성 - ORDER_3001
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [...]}'

# 포인트 조회 - POINT_5000
curl http://localhost:8081/api/points/users/1/balance
```

## 참고

- 모든 예외는 `GlobalExceptionHandler`에서 일괄 처리됩니다.
- HTTP 상태코드는 `ResponseCode`의 `httpStatus` 필드로 관리됩니다.
- 하위 호환성을 위해 기존 `ApiResponse.success()` 메서드는 `@Deprecated`로 유지됩니다.
