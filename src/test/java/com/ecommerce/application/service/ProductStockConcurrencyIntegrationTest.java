package com.ecommerce.application.service;

import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.entity.User;
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

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 재고 차감 동시성 통합 테스트
 * ExecutorService를 사용하여 멀티스레드 환경에서 재고 동시성 제어를 검증합니다.
 * MySQL 데이터베이스와 실제 JPA repository를 사용하여 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("재고 차감 동시성 통합 테스트")
class ProductStockConcurrencyIntegrationTest {

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

    @MockBean
    private DataPlatformService dataPlatformService;

    @Autowired
    private OrderService orderService;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAll();
        orderPaymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("50명이 동시에 재고 10개 상품을 1개씩 구매하면 10명만 성공한다")
    void orderProduct_Concurrency_10OutOf50() throws InterruptedException {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        Product product = new Product(null, "인기상품", "한정수량", 10000, 10, "전자");
        final Product savedProduct = productRepository.save(product);

        int threadCount = 50;  // 50명이 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= threadCount; i++) {
            final long userId = i;
            // 각 사용자 생성
            User user = new User(null, "사용자" + userId, "user" + userId + "@test.com", 100000);
            final User savedUser = userRepository.save(user);

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(savedProduct.getId(), 1);
                    OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(itemReq), null, null);
                    orderService.createOrder(request);
                    successCount.incrementAndGet();

                } catch (IllegalStateException e) {
                    // 재고가 부족합니다 - 예상된 예외
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        Product resultProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(10);  // 정확히 10명만 성공
        assertThat(failCount.get()).isEqualTo(40);     // 나머지 40명은 실패
        assertThat(resultProduct.getStockQuantity()).isEqualTo(0);  // 재고 0
    }

    @Test
    @DisplayName("20명이 동시에 재고 10개 상품을 2개씩 구매하면 5명만 성공한다")
    void orderProduct_Concurrency_BuyMultiple() throws InterruptedException {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        Product product = new Product(null, "한정상품", "2개씩 구매", 5000, 10, "의류");
        final Product savedProduct = productRepository.save(product);

        int threadCount = 20;  // 20명이 2개씩 구매 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= threadCount; i++) {
            final long userId = i;
            User user = new User(null, "사용자" + userId, "user" + userId + "@test.com", 100000);
            final User savedUser = userRepository.save(user);

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(savedProduct.getId(), 2);
                    OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(itemReq), null, null);
                    orderService.createOrder(request);
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
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        Product resultProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(5);  // 정확히 5명만 성공 (5명 * 2개 = 10개)
        assertThat(failCount.get()).isEqualTo(15);    // 나머지 15명은 실패
        assertThat(resultProduct.getStockQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("여러 사용자가 여러 상품을 동시에 주문해도 각 상품의 재고가 정확하게 차감된다")
    void orderProduct_MultipleProducts_MultipleUsers() throws InterruptedException {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        Product product1 = new Product(null, "상품1", "설명1", 10000, 5, "전자");
        Product product2 = new Product(null, "상품2", "설명2", 20000, 5, "전자");
        final Product savedProduct1 = productRepository.save(product1);
        final Product savedProduct2 = productRepository.save(product2);

        int threadCount = 10;  // 10명의 사용자가 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            // 각 스레드마다 다른 사용자 생성 (낙관적 락 충돌 방지)
            User user = new User(null, "사용자" + index, "user" + index + "@test.com", 1000000);
            final User savedUser = userRepository.save(user);

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // 번갈아가며 상품1, 상품2 주문
                    Long productId = (index % 2 == 0) ? savedProduct1.getId() : savedProduct2.getId();
                    OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(productId, 1);
                    OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(itemReq), null, null);
                    orderService.createOrder(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 재고 부족 등으로 실패
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        Product result1 = productRepository.findById(savedProduct1.getId()).orElseThrow();
        Product result2 = productRepository.findById(savedProduct2.getId()).orElseThrow();

        // 각 상품이 5개씩 있으므로 최소 8개 이상 성공해야 함
        // (낙관적 락으로 인한 일부 실패 가능성 고려)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(8);

        // 재고는 정확하게 차감되어야 함
        int totalRemaining = result1.getStockQuantity() + result2.getStockQuantity();
        int totalSold = 10 - totalRemaining;
        assertThat(totalSold).isEqualTo(successCount.get());
    }

    @Test
    @DisplayName("재고가 1개일 때 여러 명이 동시에 구매하면 1명만 성공한다")
    void orderProduct_OneStock_OnlyOneSuccess() throws InterruptedException {
        // given
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        Product product = new Product(null, "초특가", "단 1개", 100000, 1, "한정");
        final Product savedProduct = productRepository.save(product);

        int threadCount = 100;  // 100명이 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= threadCount; i++) {
            final long userId = i;
            User user = new User(null, "사용자" + userId, "user" + userId + "@test.com", 200000);
            final User savedUser = userRepository.save(user);

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(savedProduct.getId(), 1);
                    OrderRequest request = new OrderRequest(savedUser.getId(), Arrays.asList(itemReq), null, null);
                    orderService.createOrder(request);
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
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        Product resultProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(1);   // 정확히 1명만 성공
        assertThat(failCount.get()).isEqualTo(99);     // 나머지 99명은 실패
        assertThat(resultProduct.getStockQuantity()).isEqualTo(0);
    }
}
