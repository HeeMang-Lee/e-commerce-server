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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 다중 리소스 동시 사용 통합 테스트
 * 재고, 포인트, 쿠폰을 동시에 사용하는 복잡한 시나리오를 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("다중 리소스 동시 사용 통합 테스트")
class MultiResourceConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderPaymentRepository orderPaymentRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private DataPlatformService dataPlatformService;

    private Product testProduct;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // 테스트 격리를 위해 먼저 정리
        pointHistoryRepository.deleteAll();
        orderPaymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        outboxEventRepository.deleteAll();

        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // 테스트 상품 생성
        testProduct = new Product(null, "인기상품", "한정판", 10000, 10, "전자제품");
        productRepository.save(testProduct);

        // 테스트 쿠폰 생성
        testCoupon = new Coupon(
                "10% 할인 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                50,
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
    @DisplayName("여러 사용자가 재고, 포인트, 쿠폰을 동시에 사용해도 정확하게 처리된다")
    void multipleUsers_UseAllResources_Concurrently() throws InterruptedException {
        // given: 10명의 사용자 생성 (각자 충분한 포인트와 쿠폰 보유)
        int userCount = 10;
        User[] users = new User[userCount];
        UserCoupon[] userCoupons = new UserCoupon[userCount];

        for (int i = 0; i < userCount; i++) {
            users[i] = new User(null, "사용자" + i, "user" + i + "@test.com", 50000);
            userRepository.save(users[i]);

            // 각 사용자에게 쿠폰 발급
            UserCoupon coupon = new UserCoupon(users[i].getId(), testCoupon.getId(), LocalDateTime.now().plusDays(30));
            userCoupons[i] = userCouponRepository.save(coupon);

            // 쿠폰 발급 수량 증가
            couponRepository.executeWithLock(testCoupon.getId(), coupon1 -> {
                coupon1.issue();
                return null;
            });
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when: 10명이 동시에 상품 1개씩 주문 (포인트 + 쿠폰 사용)
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // 주문 생성 (쿠폰 적용)
                    OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(testProduct.getId(), 1);
                    OrderRequest orderReq = new OrderRequest(
                            users[index].getId(),
                            Arrays.asList(itemReq),
                            userCoupons[index].getId(),
                            null
                    );
                    var orderResponse = orderService.createOrder(orderReq);

                    // 결제 처리 (쿠폰 적용 후 9000원을 포인트로 결제)
                    PaymentRequest paymentReq = new PaymentRequest(null, 9000);
                    orderService.processPayment(orderResponse.orderId(), paymentReq);

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 재고 부족으로 실패 가능
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 재고 10개이므로 모두 성공
        assertThat(successCount.get()).isEqualTo(10);

        // 재고 확인
        Product afterProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(afterProduct.getStockQuantity()).isEqualTo(0);

        // 각 사용자의 포인트가 9000원 차감되었는지 확인 (10% 쿠폰 적용 후)
        for (User user : users) {
            User afterUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(afterUser.getPointBalance()).isEqualTo(41000); // 50000 - 9000
        }

        // 각 사용자의 쿠폰이 사용 상태인지 확인
        for (UserCoupon userCoupon : userCoupons) {
            UserCoupon afterCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
            assertThat(afterCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        }
    }

    @Test
    @DisplayName("재고가 부족하면 일부만 성공하고 나머지는 실패한다")
    void insufficientStock_PartialSuccess() throws InterruptedException {
        // given: 5개 재고에 10명이 시도
        int userCount = 10;
        User[] users = new User[userCount];

        // 재고를 5개로 설정
        Product limitedProduct = new Product(null, "한정상품", "5개만", 10000, 5, "전자제품");
        productRepository.save(limitedProduct);

        for (int i = 0; i < userCount; i++) {
            users[i] = new User(null, "사용자" + i, "user" + i + "@test.com", 50000);
            userRepository.save(users[i]);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 10명이 동시에 주문 시도
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(limitedProduct.getId(), 1);
                    OrderRequest orderReq = new OrderRequest(users[index].getId(), Arrays.asList(itemReq), null, null);
                    var orderResponse = orderService.createOrder(orderReq);

                    PaymentRequest paymentReq = new PaymentRequest(null, 0);
                    orderService.processPayment(orderResponse.orderId(), paymentReq);

                    successCount.incrementAndGet();

                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 정확히 5명만 성공
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        Product afterProduct = productRepository.findById(limitedProduct.getId()).orElseThrow();
        assertThat(afterProduct.getStockQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("여러 상품을 동시에 주문하면 각 상품의 재고가 정확하게 차감된다")
    void multipleProducts_ConcurrentOrders() throws InterruptedException {
        // given
        Product product1 = new Product(null, "상품A", "설명A", 5000, 5, "전자");
        Product product2 = new Product(null, "상품B", "설명B", 10000, 5, "전자");
        productRepository.save(product1);
        productRepository.save(product2);

        int userCount = 5;
        User[] users = new User[userCount];

        for (int i = 0; i < userCount; i++) {
            users[i] = new User(null, "사용자" + i, "user" + i + "@test.com", 100000);
            userRepository.save(users[i]);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when: 5명이 동시에 상품A, 상품B를 각각 1개씩 주문
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    OrderRequest.OrderItemRequest item1 = new OrderRequest.OrderItemRequest(product1.getId(), 1);
                    OrderRequest.OrderItemRequest item2 = new OrderRequest.OrderItemRequest(product2.getId(), 1);
                    OrderRequest orderReq = new OrderRequest(
                            users[index].getId(),
                            Arrays.asList(item1, item2),
                            null,
                            null
                    );
                    var orderResponse = orderService.createOrder(orderReq);

                    PaymentRequest paymentReq = new PaymentRequest(null, 0);
                    orderService.processPayment(orderResponse.orderId(), paymentReq);

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 실패
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 모두 성공
        assertThat(successCount.get()).isEqualTo(5);

        Product afterProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        Product afterProduct2 = productRepository.findById(product2.getId()).orElseThrow();

        assertThat(afterProduct1.getStockQuantity()).isEqualTo(0);
        assertThat(afterProduct2.getStockQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("포인트가 부족한 경우 결제가 실패하고 재고가 복구된다")
    void insufficientPoint_PaymentFails_StockRestored() throws InterruptedException {
        // given: 포인트가 부족한 사용자들
        int userCount = 5;
        User[] users = new User[userCount];

        for (int i = 0; i < userCount; i++) {
            users[i] = new User(null, "가난한사용자" + i, "poor" + i + "@test.com", 1000); // 포인트 부족
            userRepository.save(users[i]);
        }

        int initialStock = testProduct.getStockQuantity();

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger failCount = new AtomicInteger(0);

        // when: 포인트 부족한 상태로 주문 시도
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(testProduct.getId(), 1);
                    OrderRequest orderReq = new OrderRequest(users[index].getId(), Arrays.asList(itemReq), null, null);
                    var orderResponse = orderService.createOrder(orderReq);

                    // 과도한 포인트 사용 시도
                    PaymentRequest paymentReq = new PaymentRequest(null, 50000);
                    orderService.processPayment(orderResponse.orderId(), paymentReq);

                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 모두 실패
        assertThat(failCount.get()).isEqualTo(5);

        // 재고는 모두 복구되어야 함
        Product afterProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(afterProduct.getStockQuantity()).isEqualTo(initialStock);
    }

    @Test
    @DisplayName("성공한 주문의 모든 리소스가 정확하게 차감된다")
    void successfulOrder_AllResourcesDeducted() {
        // given
        User user = new User(null, "테스트사용자", "test@test.com", 50000);
        userRepository.save(user);

        UserCoupon userCoupon = new UserCoupon(user.getId(), testCoupon.getId(), LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon);

        couponRepository.executeWithLock(testCoupon.getId(), coupon -> {
            coupon.issue();
            return null;
        });

        int initialStock = testProduct.getStockQuantity();
        int initialPoint = user.getPointBalance();

        // when: 주문 + 결제
        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(testProduct.getId(), 2);
        OrderRequest orderReq = new OrderRequest(
                user.getId(),
                Arrays.asList(itemReq),
                userCoupon.getId(),
                5000
        );
        var orderResponse = orderService.createOrder(orderReq);

        PaymentRequest paymentReq = new PaymentRequest(null, 5000);
        orderService.processPayment(orderResponse.orderId(), paymentReq);

        // then: 모든 리소스가 정확하게 차감
        Product afterProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(afterProduct.getStockQuantity()).isEqualTo(initialStock - 2);

        User afterUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterUser.getPointBalance()).isEqualTo(initialPoint - 5000);

        UserCoupon afterCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
        assertThat(afterCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);

        // 포인트 히스토리 확인
        List<PointHistory> histories = pointHistoryRepository.findByUserId(user.getId());
        assertThat(histories).isNotEmpty();
        assertThat(histories.stream().anyMatch(h ->
                h.getTransactionType() == TransactionType.USE && h.getAmount() == 5000
        )).isTrue();
    }
}
