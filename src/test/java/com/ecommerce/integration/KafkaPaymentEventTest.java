package com.ecommerce.integration;

import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
import com.ecommerce.application.dto.PaymentRequest;
import com.ecommerce.application.dto.PaymentResponse;
import com.ecommerce.application.service.OrderService;
import com.ecommerce.config.KafkaIntegrationTestSupport;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.OutboxEventRepository;
import com.ecommerce.domain.repository.ProductRepository;
import com.ecommerce.domain.repository.UserRepository;
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

class KafkaPaymentEventTest extends KafkaIntegrationTestSupport {

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

        String uniqueEmail = "kafkatest" + System.currentTimeMillis() + "@test.com";
        testUser = new User(null, "카프카테스트유저", uniqueEmail, 100_000);
        testUser = userRepository.save(testUser);

        String uniqueProductName = "카프카테스트 상품 " + System.currentTimeMillis();
        testProduct = new Product(null, uniqueProductName, "카프카 테스트", 10_000, 100, "테스트");
        testProduct = productRepository.save(testProduct);
    }

    @Test
    @DisplayName("[Kafka] 결제 완료 시 Kafka로 이벤트가 발행되고 Consumer가 처리한다")
    void paymentCompleted_shouldPublishToKafkaAndConsumeSuccessfully() {
        // given
        OrderRequest orderRequest = new OrderRequest(
                testUser.getId(),
                List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 3)),
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

        // then - 결제는 즉시 완료
        assertThat(paymentResponse.paymentStatus()).isEqualTo("COMPLETED");

        // Kafka Consumer가 메시지를 처리할 때까지 대기
        String rankingKey = "ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            assertThat(score).isNotNull();
            assertThat(score.intValue()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("[Kafka] 여러 상품 주문 시 각 상품별 판매량이 Kafka를 통해 기록된다")
    void paymentCompleted_multipleItems_shouldRecordViaKafka() {
        // given
        Product product2 = new Product(null, "카프카테스트 상품 2", "설명", 20_000, 50, "테스트");
        product2 = productRepository.save(product2);

        OrderRequest orderRequest = new OrderRequest(
                testUser.getId(),
                List.of(
                        new OrderRequest.OrderItemRequest(testProduct.getId(), 2),
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

        Product finalProduct2 = product2;
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score1 = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            Double score2 = redisTemplate.opsForZSet().score(rankingKey, finalProduct2.getId().toString());

            assertThat(score1).isNotNull();
            assertThat(score2).isNotNull();
            assertThat(score1.intValue()).isEqualTo(2);
            assertThat(score2.intValue()).isEqualTo(5);
        });
    }
}
