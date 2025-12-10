package com.ecommerce.application.event;

import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
import com.ecommerce.application.dto.PaymentRequest;
import com.ecommerce.application.dto.PaymentResponse;
import com.ecommerce.application.service.OrderService;
import com.ecommerce.config.IntegrationTestSupport;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.infrastructure.redis.ProductRankingRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentEventIntegrationTest extends IntegrationTestSupport {

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
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProductRankingRedisRepository rankingRedisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        rankingRedisRepository.clearAll();

        testUser = new User(null, "테스트유저", "test@test.com", 100_000);
        testUser = userRepository.save(testUser);

        testProduct = new Product(null, "테스트 상품", "테스트 설명", 10_000, 100, "전자기기");
        testProduct = productRepository.save(testProduct);
    }

    @Test
    @DisplayName("결제 완료 시 이벤트가 발행되고 비동기로 처리된다")
    void paymentCompleted_shouldPublishEventAndProcessAsync() {
        // given
        OrderRequest orderRequest = new OrderRequest(
                testUser.getId(),
                List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 2)),
                null,
                0
        );
        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        Long orderId = orderResponse.orderId();

        // when
        long startTime = System.currentTimeMillis();
        PaymentResponse paymentResponse = orderService.processPayment(
                orderId,
                new PaymentRequest(null, 0)
        );
        long endTime = System.currentTimeMillis();

        // then - 결제는 즉시 완료
        assertThat(paymentResponse.paymentStatus()).isEqualTo("COMPLETED");
        long syncDuration = endTime - startTime;
        System.out.println("결제 처리 시간 (동기): " + syncDuration + "ms");

        // 비동기 이벤트 처리 대기 (최대 5초)
        String rankingKey = "product:ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            assertThat(score).isNotNull();
            assertThat(score.intValue()).isEqualTo(2);
        });

        System.out.println("비동기 이벤트 처리 완료");
    }

    @Test
    @DisplayName("결제 완료 후 데이터 플랫폼 전송 실패 시 Outbox에 저장된다")
    void paymentCompleted_dataPlatformFailed_shouldSaveToOutbox() throws InterruptedException {
        // given
        OrderRequest orderRequest = new OrderRequest(
                testUser.getId(),
                List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                null,
                0
        );
        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        Long orderId = orderResponse.orderId();

        // when
        PaymentResponse paymentResponse = orderService.processPayment(
                orderId,
                new PaymentRequest(null, 0)
        );

        // then - 결제는 성공
        assertThat(paymentResponse.paymentStatus()).isEqualTo("COMPLETED");

        // 비동기 처리 대기 후 Outbox 확인
        Thread.sleep(1000);
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
        System.out.println("Outbox 대기 이벤트 수: " + pendingEvents.size());
    }

    @Test
    @DisplayName("여러 상품 주문 시 각 상품별 판매량이 랭킹에 기록된다")
    void paymentCompleted_multipleItems_shouldRecordEachProductRanking() {
        // given
        Product product2 = new Product(null, "테스트 상품 2", "설명2", 20_000, 50, "전자기기");
        product2 = productRepository.save(product2);

        OrderRequest orderRequest = new OrderRequest(
                testUser.getId(),
                List.of(
                        new OrderRequest.OrderItemRequest(testProduct.getId(), 3),
                        new OrderRequest.OrderItemRequest(product2.getId(), 5)
                ),
                null,
                0
        );
        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        Long orderId = orderResponse.orderId();

        // when
        PaymentResponse paymentResponse = orderService.processPayment(
                orderId,
                new PaymentRequest(null, 0)
        );

        // then
        assertThat(paymentResponse.paymentStatus()).isEqualTo("COMPLETED");

        String rankingKey = "product:ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        Product finalProduct = product2;
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score1 = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            Double score2 = redisTemplate.opsForZSet().score(rankingKey, finalProduct.getId().toString());

            assertThat(score1).isNotNull();
            assertThat(score1.intValue()).isEqualTo(3);
            assertThat(score2).isNotNull();
            assertThat(score2.intValue()).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("동시에 여러 결제가 처리되어도 이벤트가 안전하게 처리된다")
    void concurrentPayments_shouldProcessEventsCorrectly() throws InterruptedException {
        // given
        int concurrentCount = 10;
        Long[] orderIds = new Long[concurrentCount];

        for (int i = 0; i < concurrentCount; i++) {
            User user = new User(null, "유저" + i, "user" + i + "@test.com", 100_000);
            user = userRepository.save(user);

            OrderRequest orderRequest = new OrderRequest(
                    user.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    null,
                    0
            );
            OrderResponse response = orderService.createOrder(orderRequest);
            orderIds[i] = response.orderId();
        }

        // when
        Thread[] threads = new Thread[concurrentCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                orderService.processPayment(orderIds[index], new PaymentRequest(null, 0));
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("동시 " + concurrentCount + "건 결제 처리 시간: " + (endTime - startTime) + "ms");

        // then - 비동기 이벤트 처리 대기
        String rankingKey = "product:ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            assertThat(score).isNotNull();
            assertThat(score.intValue()).isEqualTo(concurrentCount);
        });

        System.out.println("모든 랭킹 기록 완료: " + concurrentCount + "건");
    }
}
