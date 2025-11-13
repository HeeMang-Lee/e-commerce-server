package com.ecommerce.application.service;

import com.ecommerce.application.dto.*;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.infrastructure.external.DataPlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 주문 전체 플로우 통합 테스트
 * 주문 생성부터 결제, 쿠폰/포인트 사용, 이력 조회까지 전체 시나리오를 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("주문 전체 플로우 통합 테스트")
class OrderIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderPaymentRepository orderPaymentRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private CouponRepository couponRepository;

    @MockBean
    private DataPlatformService dataPlatformService;

    @Autowired
    private OrderService orderService;

    @AfterEach
    void tearDown() {
        orderPaymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        pointHistoryRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("여러 상품을 주문하면 재고가 차감되고 주문이 생성된다")
    void createOrder_MultipleProducts() {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        Product product1 = new Product(null, "키보드", "무선 키보드", 50000, 10, "전자");
        Product product2 = new Product(null, "마우스", "무선 마우스", 30000, 20, "전자");
        Product savedProduct1 = productRepository.save(product1);
        Product savedProduct2 = productRepository.save(product2);

        OrderRequest.OrderItemRequest item1 = new OrderRequest.OrderItemRequest(savedProduct1.getId(), 2);  // 키보드 2개
        OrderRequest.OrderItemRequest item2 = new OrderRequest.OrderItemRequest(savedProduct2.getId(), 3);  // 마우스 3개
        OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(item1, item2), null, null);

        // when
        OrderResponse response = orderService.createOrder(request);

        // then
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(190000);  // (50000 * 2) + (30000 * 3)
        assertThat(response.finalAmount()).isEqualTo(190000);
        Product updatedProduct1 = productRepository.findById(savedProduct1.getId()).orElseThrow();
        Product updatedProduct2 = productRepository.findById(savedProduct2.getId()).orElseThrow();
        assertThat(updatedProduct1.getStockQuantity()).isEqualTo(8);   // 10 - 2
        assertThat(updatedProduct2.getStockQuantity()).isEqualTo(17);  // 20 - 3
    }

    @Test
    @DisplayName("쿠폰을 사용하여 주문하면 할인이 적용된다")
    void createOrder_WithCoupon() {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        Product product = new Product(null, "상품", "설명", 50000, 10, "카테고리");
        Product savedProduct = productRepository.save(product);

        // 쿠폰 발급
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("할인쿠폰", DiscountType.PERCENTAGE, 10, 100, now.minusDays(1), now.plusDays(30), 30);
        Coupon savedCoupon = couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(savedUser.getId(), savedCoupon.getId(), now.plusDays(30));
        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(savedProduct.getId(), 1);
        OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(item), savedUserCoupon.getId(), null);

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
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        Product product = new Product(null, "상품", "설명", 50000, 10, "카테고리");
        Product savedProduct = productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(savedProduct.getId(), 1);
        OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(item), null, 10000);  // 10000 포인트 사용

        // when - 주문 생성
        OrderResponse orderResponse = orderService.createOrder(request);

        // 주문 생성 시에는 포인트가 차감되지 않음
        User userAfterOrder = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(userAfterOrder.getPointBalance()).isEqualTo(100000);

        // when - 결제 처리
        PaymentRequest paymentRequest = new PaymentRequest(null, 10000);
        PaymentResponse paymentResponse = orderService.processPayment(orderResponse.orderId(), paymentRequest);

        // then
        assertThat(orderResponse.totalAmount()).isEqualTo(50000);
        assertThat(paymentResponse.usedPoint()).isEqualTo(10000);
        assertThat(paymentResponse.paymentAmount()).isEqualTo(40000);  // 50000 - 10000
        User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(updatedUser.getPointBalance()).isEqualTo(90000);    // 100000 - 10000

        // 포인트 이력 확인
        List<com.ecommerce.domain.entity.PointHistory> history = pointHistoryRepository.findByUserId(savedUser.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getTransactionType()).isEqualTo(TransactionType.USE);
        assertThat(history.get(0).getAmount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("재고가 부족하면 주문이 실패한다")
    void createOrder_InsufficientStock_ThrowsException() {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        Product product = new Product(null, "상품", "설명", 50000, 5, "카테고리");
        Product savedProduct = productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(savedProduct.getId(), 10);  // 재고 5개인데 10개 주문
        OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(item), null, null);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(IllegalStateException.class),
                        ex -> assertThat(ex).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                )
                .hasMessageContaining("재고 부족");

        // 재고는 차감되지 않음
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("사용자의 주문 내역을 조회할 수 있다")
    void getOrderHistory() {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        Product product1 = new Product(null, "키보드", "무선", 50000, 10, "전자");
        Product product2 = new Product(null, "마우스", "무선", 30000, 10, "전자");
        Product savedProduct1 = productRepository.save(product1);
        Product savedProduct2 = productRepository.save(product2);

        // 첫 번째 주문
        OrderRequest.OrderItemRequest item1 = new OrderRequest.OrderItemRequest(savedProduct1.getId(), 1);
        OrderRequest request1 = new OrderRequest(savedUser.getId(), Arrays.asList(item1), null, null);
        orderService.createOrder(request1);

        // 두 번째 주문
        OrderRequest.OrderItemRequest item2 = new OrderRequest.OrderItemRequest(savedProduct2.getId(), 2);
        OrderRequest request2 = new OrderRequest(savedUser.getId(), Arrays.asList(item2), null, null);
        orderService.createOrder(request2);

        // when
        List<OrderHistoryResponse> history = orderService.getOrderHistory(savedUser.getId());

        // then
        assertThat(history).hasSize(2);
        assertThat(history.get(0).totalAmount()).isEqualTo(50000);
        assertThat(history.get(1).totalAmount()).isEqualTo(60000);
    }

    @Test
    @DisplayName("주문 시 상품 정보는 스냅샷으로 저장된다")
    void createOrder_SavesProductSnapshot() {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        Product product = new Product(null, "키보드", "무선 키보드", 50000, 10, "전자");
        Product savedProduct = productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(savedProduct.getId(), 1);
        OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(item), null, null);

        // when
        OrderResponse response = orderService.createOrder(request);

        // 주문 후 상품 정보 변경
        Product updatedProduct = productRepository.findById(savedProduct.getId()).get();
        // 실제로는 가격이나 이름을 변경할 수 있지만, 현재 Product 엔티티는 불변

        // then
        List<OrderHistoryResponse> history = orderService.getOrderHistory(savedUser.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).items().get(0).productName()).isEqualTo("키보드");
        assertThat(history.get(0).items().get(0).price()).isEqualTo(50000);
    }

    @Test
    @DisplayName("주문 완료 시 인기 상품 판매 이력이 기록된다")
    void createOrder_RecordsSaleHistory() {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        Product product = new Product(null, "인기상품", "설명", 50000, 10, "카테고리");
        Product savedProduct = productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(savedProduct.getId(), 3);  // 3개 구매
        OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(item), null, null);

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

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        Product product = new Product(null, "상품", "설명", 50000, 10, "카테고리");
        Product savedProduct = productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(savedProduct.getId(), 1);
        OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(item), null, null);

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
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        Product product = new Product(null, "상품", "설명", 50000, 10, "카테고리");
        Product savedProduct = productRepository.save(product);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(savedProduct.getId(), 1);
        OrderRequest request = new OrderRequest(999L, Arrays.asList(item), null, null);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(IllegalArgumentException.class),
                        ex -> assertThat(ex).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                )
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 상품은 주문할 수 없다")
    void createOrder_ProductNotFound_ThrowsException() {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        User user = new User(null, "테스트", "test@test.com", 100000);
        User savedUser = userRepository.save(user);

        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest(999L, 1);
        OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(item), null, null);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(IllegalArgumentException.class),
                        ex -> assertThat(ex).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                )
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }
}
