package com.ecommerce.application.service;

import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
import com.ecommerce.application.dto.PaymentRequest;
import com.ecommerce.application.dto.PaymentResponse;
import com.ecommerce.config.IntegrationTestSupport;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.domain.service.PaymentDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

@DisplayName("결제 보상 트랜잭션 테스트")
class PaymentCompensationTest extends IntegrationTestSupport {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private OrderPaymentRepository orderPaymentRepository;

    @SpyBean
    private PaymentDomainService paymentDomainService;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        testUser = userRepository.save(
                new User(null, "테스트유저", "comp_test_" + uniqueSuffix + "@test.com", 100_000)
        );
        testProduct = productRepository.save(
                new Product(null, "테스트상품_" + uniqueSuffix, "설명", 10_000, 100, "전자기기")
        );
    }

    @Nested
    @DisplayName("보상 트랜잭션 시나리오")
    class CompensationScenarios {

        @Test
        @DisplayName("결제 완료 단계에서 실패 시 포인트가 복구된다")
        void whenPaymentFails_pointShouldBeRestored() {
            // given
            int initialPoint = testUser.getPointBalance();
            int usePoint = 5_000;

            OrderRequest orderRequest = new OrderRequest(
                    testUser.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    null,
                    usePoint
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);

            // 결제 완료 단계에서 강제로 예외 발생
            doThrow(new RuntimeException("결제 시스템 오류"))
                    .when(paymentDomainService).completePayment(anyLong());

            // when & then
            assertThatThrownBy(() ->
                    orderService.processPayment(orderResponse.orderId(), new PaymentRequest(null, usePoint))
            ).isInstanceOf(RuntimeException.class);

            // 포인트가 복구되었는지 확인
            User reloadedUser = userRepository.getByIdOrThrow(testUser.getId());
            assertThat(reloadedUser.getPointBalance()).isEqualTo(initialPoint);
        }

        @Test
        @DisplayName("결제 완료 단계에서 실패 시 쿠폰이 복구된다")
        void whenPaymentFails_couponShouldBeRestored() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Coupon coupon = couponRepository.save(
                    new Coupon("테스트쿠폰", DiscountType.FIXED_AMOUNT, 1000, 100,
                            now.minusDays(1), now.plusDays(30), 30)
            );
            UserCoupon userCoupon = userCouponRepository.save(
                    new UserCoupon(testUser.getId(), coupon.getId(), LocalDateTime.now().plusDays(30))
            );

            OrderRequest orderRequest = new OrderRequest(
                    testUser.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    userCoupon.getId(),
                    0
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);

            doThrow(new RuntimeException("결제 시스템 오류"))
                    .when(paymentDomainService).completePayment(anyLong());

            // when & then
            assertThatThrownBy(() ->
                    orderService.processPayment(orderResponse.orderId(), new PaymentRequest(null, 0))
            ).isInstanceOf(RuntimeException.class);

            // 쿠폰이 복구되었는지 확인
            UserCoupon reloadedCoupon = userCouponRepository.getByIdOrThrow(userCoupon.getId());
            assertThat(reloadedCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        }

        @Test
        @DisplayName("결제 완료 단계에서 실패 시 재고가 복구된다")
        void whenPaymentFails_stockShouldBeRestored() {
            // given
            int initialStock = testProduct.getStockQuantity();
            int orderQuantity = 5;

            OrderRequest orderRequest = new OrderRequest(
                    testUser.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), orderQuantity)),
                    null,
                    0
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);

            // 주문 생성 후 재고 확인 (차감됨)
            Product afterOrder = productRepository.getByIdOrThrow(testProduct.getId());
            assertThat(afterOrder.getStockQuantity()).isEqualTo(initialStock - orderQuantity);

            doThrow(new RuntimeException("결제 시스템 오류"))
                    .when(paymentDomainService).completePayment(anyLong());

            // when & then
            assertThatThrownBy(() ->
                    orderService.processPayment(orderResponse.orderId(), new PaymentRequest(null, 0))
            ).isInstanceOf(RuntimeException.class);

            // 재고가 복구되었는지 확인
            Product reloadedProduct = productRepository.getByIdOrThrow(testProduct.getId());
            assertThat(reloadedProduct.getStockQuantity()).isEqualTo(initialStock);
        }

        @Test
        @DisplayName("결제 실패 시 결제 상태가 FAILED로 변경된다")
        void whenPaymentFails_statusShouldBeFailed() {
            // given
            OrderRequest orderRequest = new OrderRequest(
                    testUser.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    null,
                    0
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);

            doThrow(new RuntimeException("결제 시스템 오류"))
                    .when(paymentDomainService).completePayment(anyLong());

            // when & then
            assertThatThrownBy(() ->
                    orderService.processPayment(orderResponse.orderId(), new PaymentRequest(null, 0))
            ).isInstanceOf(RuntimeException.class);

            // 결제 상태 확인
            OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderResponse.orderId());
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("동시성 시나리오")
    class ConcurrencyScenarios {

        @Test
        @DisplayName("동일 주문에 대한 동시 결제 시도 시 하나만 성공한다")
        void concurrentPaymentOnSameOrder_onlyOneSucceeds() throws InterruptedException {
            // given
            OrderRequest orderRequest = new OrderRequest(
                    testUser.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    null,
                    0
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);
            Long orderId = orderResponse.orderId();

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        orderService.processPayment(orderId, new PaymentRequest(null, 0));
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(threadCount - 1);

            OrderPayment payment = orderPaymentRepository.getByOrderIdOrThrow(orderId);
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("이미 완료된 결제에 대한 재시도는 실패한다")
        void paymentOnAlreadyCompletedOrder_shouldFail() {
            // given
            OrderRequest orderRequest = new OrderRequest(
                    testUser.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    null,
                    0
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);
            Long orderId = orderResponse.orderId();

            // 첫 번째 결제 성공
            PaymentResponse firstPayment = orderService.processPayment(orderId, new PaymentRequest(null, 0));
            assertThat(firstPayment.paymentStatus()).isEqualTo("COMPLETED");

            // when & then - 두 번째 결제 시도는 실패해야 함
            assertThatThrownBy(() ->
                    orderService.processPayment(orderId, new PaymentRequest(null, 0))
            ).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 완료된 결제");
        }
    }

    @Nested
    @DisplayName("정상 시나리오")
    class HappyPathScenarios {

        @Test
        @DisplayName("포인트와 쿠폰을 함께 사용한 결제가 정상 처리된다")
        void paymentWithPointAndCoupon_shouldSucceed() {
            // given
            int initialPoint = testUser.getPointBalance();
            int usePoint = 3_000;

            LocalDateTime now = LocalDateTime.now();
            Coupon coupon = couponRepository.save(
                    new Coupon("할인쿠폰", DiscountType.FIXED_AMOUNT, 2000, 100,
                            now.minusDays(1), now.plusDays(30), 30)
            );
            UserCoupon userCoupon = userCouponRepository.save(
                    new UserCoupon(testUser.getId(), coupon.getId(), LocalDateTime.now().plusDays(30))
            );

            OrderRequest orderRequest = new OrderRequest(
                    testUser.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    userCoupon.getId(),
                    usePoint
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);

            // when
            PaymentResponse paymentResponse = orderService.processPayment(
                    orderResponse.orderId(),
                    new PaymentRequest(userCoupon.getId(), usePoint)
            );

            // then
            assertThat(paymentResponse.paymentStatus()).isEqualTo("COMPLETED");

            // 포인트 차감 확인
            User reloadedUser = userRepository.getByIdOrThrow(testUser.getId());
            assertThat(reloadedUser.getPointBalance()).isEqualTo(initialPoint - usePoint);

            // 쿠폰 사용 확인
            UserCoupon reloadedCoupon = userCouponRepository.getByIdOrThrow(userCoupon.getId());
            assertThat(reloadedCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        }
    }
}
