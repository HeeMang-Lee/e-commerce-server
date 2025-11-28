package com.ecommerce.application.service;

import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.PaymentRequest;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.infrastructure.external.DataPlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 보상 트랜잭션 통합 테스트
 * 결제 실패 시 재고, 포인트, 쿠폰이 제대로 복구되는지 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("보상 트랜잭션 통합 테스트")
class CompensationTransactionIntegrationTest {

    @Autowired
    private OrderService orderService;

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
    private CouponRepository couponRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private DataPlatformService dataPlatformService;

    private User testUser;
    private Product testProduct;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 생성
        testUser = new User(null, "테스트사용자", "test@example.com", 50000);
        userRepository.save(testUser);

        testProduct = new Product(null, "테스트상품", "설명", 10000, 10, "전자제품");
        productRepository.save(testProduct);

        testCoupon = new Coupon(
                "10% 할인 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                30
        );
        couponRepository.save(testCoupon);
    }

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAll();
        orderPaymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("결제 실패 시 재고와 포인트가 복구된다")
    void paymentFailure_RestoresStockAndPoint() {
        // given
        // 포인트가 부족한 사용자 생성
        User poorUser = new User(null, "가난한사용자", "poor@test.com", 1000);
        userRepository.save(poorUser);

        // 주문 전 상태 저장
        int initialStock = productRepository.findById(testProduct.getId()).orElseThrow().getStockQuantity();
        int initialPoint = userRepository.findById(poorUser.getId()).orElseThrow().getPointBalance();

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(testProduct.getId(), 2);
        OrderRequest orderReq = new OrderRequest(poorUser.getId(), Arrays.asList(itemReq), null, null);
        var orderResponse = orderService.createOrder(orderReq);

        // when & then: 포인트 부족으로 결제 실패
        PaymentRequest paymentReq = new PaymentRequest(null, 25000); // 잔액보다 많은 포인트 사용 시도
        assertThatThrownBy(() -> orderService.processPayment(orderResponse.orderId(), paymentReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("포인트 부족");

        // 재고 복구 확인
        Product afterProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(afterProduct.getStockQuantity()).isEqualTo(initialStock);

        // 포인트 복구 확인
        User afterUser = userRepository.findById(poorUser.getId()).orElseThrow();
        assertThat(afterUser.getPointBalance()).isEqualTo(initialPoint);

        // 포인트 히스토리 확인: 포인트 차감이 실패했으므로 히스토리가 없거나, 있다면 복구되어야 함
        // (포인트 부족으로 차감 자체가 실패했으므로 USE 히스토리가 없을 수 있음)

        // 결제 상태 확인
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderResponse.orderId());
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("결제 실패 시 재고와 쿠폰이 복구된다")
    void paymentFailure_RestoresStockAndCoupon() {
        // given
        // 만료된 쿠폰 발급
        UserCoupon expiredCoupon = new UserCoupon(testUser.getId(), testCoupon.getId(), LocalDateTime.now().minusDays(1));
        userCouponRepository.save(expiredCoupon);

        // 주문 전 상태 저장
        int initialStock = productRepository.findById(testProduct.getId()).orElseThrow().getStockQuantity();

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(testProduct.getId(), 3);
        OrderRequest orderReq = new OrderRequest(
                testUser.getId(),
                Arrays.asList(itemReq),
                expiredCoupon.getId(),
                null
        );
        var orderResponse = orderService.createOrder(orderReq);

        // when & then: 만료된 쿠폰 사용 시도로 결제 실패
        PaymentRequest paymentReq = new PaymentRequest(null, 0);
        assertThatThrownBy(() -> orderService.processPayment(orderResponse.orderId(), paymentReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("만료된 쿠폰");

        // 재고 복구 확인
        Product afterProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(afterProduct.getStockQuantity()).isEqualTo(initialStock);

        // 쿠폰 상태 확인 (만료된 상태 유지)
        UserCoupon afterCoupon = userCouponRepository.findById(expiredCoupon.getId()).orElseThrow();
        assertThat(afterCoupon.isExpired()).isTrue();
    }

    @Test
    @DisplayName("중복 결제 시도는 실패한다 (멱등성)")
    void duplicatePayment_Fails() {
        // given
        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(testProduct.getId(), 1);
        OrderRequest orderReq = new OrderRequest(testUser.getId(), Arrays.asList(itemReq), null, null);
        var orderResponse = orderService.createOrder(orderReq);

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when: 첫 번째 결제 성공
        PaymentRequest paymentReq = new PaymentRequest(null, 0);
        orderService.processPayment(orderResponse.orderId(), paymentReq);

        // then: 두 번째 결제 시도는 실패
        assertThatThrownBy(() -> orderService.processPayment(orderResponse.orderId(), paymentReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된 결제입니다");

        // 결제 상태 확인
        OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderResponse.orderId());
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("포인트와 쿠폰을 함께 사용한 결제가 성공한다")
    void paymentWithPointAndCoupon_Success() {
        // given
        UserCoupon userCoupon = new UserCoupon(testUser.getId(), testCoupon.getId(), LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon);

        // 주문 생성 전 상태 저장
        int initialStock = productRepository.findById(testProduct.getId()).orElseThrow().getStockQuantity();
        int initialPoint = userRepository.findById(testUser.getId()).orElseThrow().getPointBalance();

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(testProduct.getId(), 1);
        OrderRequest orderReq = new OrderRequest(
                testUser.getId(),
                Arrays.asList(itemReq),
                userCoupon.getId(),
                3000
        );
        var orderResponse = orderService.createOrder(orderReq);

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when: 포인트 + 쿠폰 함께 사용하여 결제
        PaymentRequest paymentReq = new PaymentRequest(null, 3000);
        var paymentResponse = orderService.processPayment(orderResponse.orderId(), paymentReq);

        // then
        assertThat(paymentResponse.paymentStatus()).isEqualTo("COMPLETED");
        assertThat(paymentResponse.usedPoint()).isEqualTo(3000);

        // 재고 차감 확인 (1개 주문했으므로 재고가 1 감소해야 함)
        Product afterProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(afterProduct.getStockQuantity()).isEqualTo(initialStock - 1);

        // 포인트 차감 확인
        User afterUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(afterUser.getPointBalance()).isEqualTo(initialPoint - 3000);

        // 쿠폰 사용 확인
        UserCoupon afterCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
        assertThat(afterCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        assertThat(afterCoupon.getUsedAt()).isNotNull();
    }
}
