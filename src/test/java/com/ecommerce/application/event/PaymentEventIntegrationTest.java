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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PaymentEventIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

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

        String uniqueEmail = "eventtest" + System.currentTimeMillis() + "@test.com";
        testUser = new User(null, "테스트유저", uniqueEmail, 100_000);
        testUser = userRepository.save(testUser);

        String uniqueProductName = "이벤트테스트 상품 " + System.currentTimeMillis();
        testProduct = new Product(null, uniqueProductName, "테스트 설명", 10_000, 100, "전자기기");
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
        String rankingKey = "ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            assertThat(score).isNotNull();
            assertThat(score.intValue()).isEqualTo(2);
        });

        System.out.println("비동기 이벤트 처리 완료");
    }

    @Test
    @DisplayName("여러 상품 주문 시 각 상품별 판매량이 랭킹에 기록된다")
    void paymentCompleted_multipleItems_shouldRecordEachProductRanking() {
        // given
        Product product2 = new Product(null, "이벤트테스트 상품 2", "설명2", 20_000, 50, "전자기기");
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

        String rankingKey = "ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        Product finalProduct = product2;
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score1 = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            Double score2 = redisTemplate.opsForZSet().score(rankingKey, finalProduct.getId().toString());

            assertThat(score1).isNotNull();
            assertThat(score2).isNotNull();
        });

        System.out.println("여러 상품 랭킹 기록 완료");
    }
}
