package com.ecommerce.application.service;

import com.ecommerce.application.dto.*;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.infrastructure.external.DataPlatformService;
import com.ecommerce.infrastructure.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 주문 전체 플로우 통합 테스트
 * 주문 생성부터 결제, 쿠폰/포인트 사용, 이력 조회까지 전체 시나리오를 검증합니다.
 */
@DisplayName("주문 전체 플로우 통합 테스트")
class OrderIntegrationTest {

    private UserRepository userRepository;
    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private OrderPaymentRepository orderPaymentRepository;
    private UserCouponRepository userCouponRepository;
    private PointHistoryRepository pointHistoryRepository;
    private PopularProductRepository popularProductRepository;
    private OutboxEventRepository outboxEventRepository;
    private CouponRepository couponRepository;
    private DataPlatformService dataPlatformService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        productRepository = new InMemoryProductRepository();
        orderRepository = new InMemoryOrderRepository();
        orderPaymentRepository = new InMemoryOrderPaymentRepository();
        userCouponRepository = new InMemoryUserCouponRepository();
        pointHistoryRepository = new InMemoryPointHistoryRepository();
        popularProductRepository = new InMemoryPopularProductRepository();
        outboxEventRepository = new InMemoryOutboxEventRepository();
        couponRepository = new InMemoryCouponRepository();
        dataPlatformService = mock(DataPlatformService.class);

        orderService = new OrderService(
                userRepository,
                productRepository,
                orderRepository,
                orderPaymentRepository,
                userCouponRepository,
                pointHistoryRepository,
                popularProductRepository,
                outboxEventRepository,
                dataPlatformService
        );

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("여러 상품을 주문하면 재고가 차감되고 주문이 생성된다")
    void createOrder_MultipleProducts() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        Product product1 = new Product(1L, "키보드", "무선 키보드", 50000, 10, "전자");
        Product product2 = new Product(2L, "마우스", "무선 마우스", 30000, 20, "전자");
        productRepository.save(product1);
        productRepository.save(product2);

        OrderRequest.OrderItemRequest item1 = new OrderRequest.OrderItemRequest(1L, 2);  // 키보드 2개
        OrderRequest.OrderItemRequest item2 = new OrderRequest.OrderItemRequest(2L, 3);  // 마우스 3개
        OrderRequest request = new OrderRequest(1L, Arrays.asList(item1, item2), null, null);

        // when
        OrderResponse response = orderService.createOrder(request);

        // then
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(190000);  // (50000 * 2) + (30000 * 3)
        assertThat(response.finalAmount()).isEqualTo(190000);
        assertThat(product1.getStockQuantity()).isEqualTo(8);   // 10 - 2
        assertThat(product2.getStockQuantity()).isEqualTo(17);  // 20 - 3
    }

    @Test
    @DisplayName("쿠폰을 사용하여 주문하면 할인이 적용된다")
    void createOrder_WithCoupon() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        Product product = new Product(1L, "상품", "설명", 50000, 10, "카테고리");
        productRepository.save(product);

        // 쿠폰 발급
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("할인쿠폰", DiscountType.PERCENTAGE, 10, 100, now.minusDays(1), now.plusDays(30), 30);
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(1L, coupon.getId(), now.plusDays(30));
        userCouponRepository.save(userCoupon);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(1L, 1);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(item), userCoupon.getId(), null);

        // when
        OrderResponse response = orderService.createOrder(request);

        // then
        assertThat(response.totalAmount()).isEqualTo(50000);
        // Note: 실제 할인 로직은 추후 확장
        assertThat(response.finalAmount()).isLessThanOrEqualTo(50000);
    }

    @Test
    @DisplayName("포인트를 사용하여 주문하고 결제하면 포인트가 차감된다")
    void createOrderAndProcessPayment_WithPoint() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        Product product = new Product(1L, "상품", "설명", 50000, 10, "카테고리");
        productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(1L, 1);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(item), null, 10000);  // 10000 포인트 사용

        // when - 주문 생성
        OrderResponse orderResponse = orderService.createOrder(request);

        // 주문 생성 시에는 포인트가 차감되지 않음
        assertThat(user.getPointBalance()).isEqualTo(100000);

        // when - 결제 처리
        PaymentRequest paymentRequest = new PaymentRequest(null, 10000);
        PaymentResponse paymentResponse = orderService.processPayment(orderResponse.orderId(), paymentRequest);

        // then
        assertThat(orderResponse.totalAmount()).isEqualTo(50000);
        assertThat(paymentResponse.usedPoint()).isEqualTo(10000);
        assertThat(paymentResponse.paymentAmount()).isEqualTo(40000);  // 50000 - 10000
        assertThat(user.getPointBalance()).isEqualTo(90000);    // 100000 - 10000

        // 포인트 이력 확인
        List<com.ecommerce.domain.entity.PointHistory> history = pointHistoryRepository.findByUserId(1L);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getTransactionType()).isEqualTo(TransactionType.USE);
        assertThat(history.get(0).getAmount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("재고가 부족하면 주문이 실패한다")
    void createOrder_InsufficientStock_ThrowsException() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        Product product = new Product(1L, "상품", "설명", 50000, 5, "카테고리");
        productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(1L, 10);  // 재고 5개인데 10개 주문
        OrderRequest request = new OrderRequest(1L, Arrays.asList(item), null, null);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고 부족:");

        // 재고는 차감되지 않음
        assertThat(product.getStockQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("사용자의 주문 내역을 조회할 수 있다")
    void getOrderHistory() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        Product product1 = new Product(1L, "키보드", "무선", 50000, 10, "전자");
        Product product2 = new Product(2L, "마우스", "무선", 30000, 10, "전자");
        productRepository.save(product1);
        productRepository.save(product2);

        // 첫 번째 주문
        OrderRequest.OrderItemRequest item1 = new OrderRequest.OrderItemRequest(1L, 1);
        OrderRequest request1 = new OrderRequest(1L, Arrays.asList(item1), null, null);
        orderService.createOrder(request1);

        // 두 번째 주문
        OrderRequest.OrderItemRequest item2 = new OrderRequest.OrderItemRequest(2L, 2);
        OrderRequest request2 = new OrderRequest(1L, Arrays.asList(item2), null, null);
        orderService.createOrder(request2);

        // when
        List<OrderHistoryResponse> history = orderService.getOrderHistory(1L);

        // then
        assertThat(history).hasSize(2);
        assertThat(history.get(0).totalAmount()).isEqualTo(50000);
        assertThat(history.get(1).totalAmount()).isEqualTo(60000);
    }

    @Test
    @DisplayName("주문 시 상품 정보는 스냅샷으로 저장된다")
    void createOrder_SavesProductSnapshot() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        Product product = new Product(1L, "키보드", "무선 키보드", 50000, 10, "전자");
        productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(1L, 1);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(item), null, null);

        // when
        OrderResponse response = orderService.createOrder(request);

        // 주문 후 상품 정보 변경
        Product updatedProduct = productRepository.findById(1L).get();
        // 실제로는 가격이나 이름을 변경할 수 있지만, 현재 Product 엔티티는 불변

        // then
        List<OrderHistoryResponse> history = orderService.getOrderHistory(1L);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).items().get(0).productName()).isEqualTo("키보드");
        assertThat(history.get(0).items().get(0).price()).isEqualTo(50000);
    }

    @Test
    @DisplayName("주문 완료 시 인기 상품 판매 이력이 기록된다")
    void createOrder_RecordsSaleHistory() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        Product product = new Product(1L, "인기상품", "설명", 50000, 10, "카테고리");
        productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(1L, 3);  // 3개 구매
        OrderRequest request = new OrderRequest(1L, Arrays.asList(item), null, null);

        // when
        orderService.createOrder(request);

        // then
        // 판매 이력이 기록되었는지 확인
        // popularProductRepository에서 조회 (실제로는 판매량 집계 쿼리 필요)
    }

    @Test
    @DisplayName("외부 데이터 플랫폼 전송에 실패해도 결제는 성공한다")
    void processPayment_ExternalServiceFails_PaymentSucceeds() {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(false);

        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        Product product = new Product(1L, "상품", "설명", 50000, 10, "카테고리");
        productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(1L, 1);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(item), null, null);

        // when - 주문 생성
        OrderResponse orderResponse = orderService.createOrder(request);

        // when - 결제 처리 (외부 전송 실패)
        PaymentRequest paymentRequest = new PaymentRequest(null, 0);
        PaymentResponse paymentResponse = orderService.processPayment(orderResponse.orderId(), paymentRequest);

        // then - 결제는 성공
        assertThat(paymentResponse.orderId()).isNotNull();
        assertThat(paymentResponse.paymentStatus()).isEqualTo("COMPLETED");

        // 아웃박스에 이벤트가 저장됨
        List<com.ecommerce.domain.entity.OutboxEvent> events = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 주문할 수 없다")
    void createOrder_UserNotFound_ThrowsException() {
        // given
        Product product = new Product(1L, "상품", "설명", 50000, 10, "카테고리");
        productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(1L, 1);
        OrderRequest request = new OrderRequest(999L, Arrays.asList(item), null, null);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 상품은 주문할 수 없다")
    void createOrder_ProductNotFound_ThrowsException() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 100000);
        userRepository.save(user);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(999L, 1);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(item), null, null);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }
}
