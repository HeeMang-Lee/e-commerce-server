package com.ecommerce.benchmark;

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
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka 성능 벤치마크 테스트
 *
 * Spring Event와 동일한 시나리오로 Kafka 성능 측정.
 * 결과는 docs/12_KAFKA_MIGRATION_REPORT.md에 기록.
 */
class KafkaBenchmarkTest extends KafkaIntegrationTestSupport {

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

        String uniqueEmail = "kafkabenchmark" + System.currentTimeMillis() + "@test.com";
        testUser = new User(null, "카프카벤치마크유저", uniqueEmail, 10_000_000);
        testUser = userRepository.save(testUser);

        String uniqueProductName = "카프카벤치마크 상품 " + System.currentTimeMillis();
        testProduct = new Product(null, uniqueProductName, "카프카 벤치마크 테스트", 100, 100_000, "테스트");
        testProduct = productRepository.save(testProduct);
    }

    @Test
    @DisplayName("[Kafka] 단일 결제 이벤트 처리 시간 측정")
    void benchmark_singlePayment() {
        // given
        OrderRequest orderRequest = new OrderRequest(
                testUser.getId(),
                List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                null,
                0
        );
        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        Long orderId = orderResponse.orderId();

        // when - 결제 처리 (동기 부분 + Kafka 발행)
        long startTime = System.nanoTime();
        PaymentResponse paymentResponse = orderService.processPayment(
                orderId,
                new PaymentRequest(null, 0)
        );
        long syncEndTime = System.nanoTime();

        // then - 동기 처리 시간
        assertThat(paymentResponse.paymentStatus()).isEqualTo("COMPLETED");
        double syncDurationMs = (syncEndTime - startTime) / 1_000_000.0;

        // Kafka Consumer가 메시지를 처리할 때까지 대기
        String rankingKey = "ranking:daily:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            assertThat(score).isNotNull();
        });
        long asyncEndTime = System.nanoTime();
        double totalDurationMs = (asyncEndTime - startTime) / 1_000_000.0;

        System.out.println("========================================");
        System.out.println("[Kafka] 단일 결제 이벤트 벤치마크");
        System.out.println("========================================");
        System.out.println("동기 처리 시간 (결제 + Kafka 발행): " + String.format("%.2f", syncDurationMs) + " ms");
        System.out.println("전체 처리 시간 (Consumer 처리 포함): " + String.format("%.2f", totalDurationMs) + " ms");
        System.out.println("Kafka Consumer 처리 시간: " + String.format("%.2f", (totalDurationMs - syncDurationMs)) + " ms");
        System.out.println("========================================");
    }

    @Test
    @DisplayName("[Kafka] 동시 100건 결제 이벤트 처리 시간 측정")
    void benchmark_concurrent100Payments() throws InterruptedException {
        // given
        int concurrentCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();

        // 주문 미리 생성
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < concurrentCount; i++) {
            String email = "kafkaconcurrent" + System.currentTimeMillis() + "_" + i + "@test.com";
            User user = userRepository.save(new User(null, "카프카동시성" + i, email, 100_000));

            OrderRequest orderRequest = new OrderRequest(
                    user.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    null,
                    0
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);
            orderIds.add(orderResponse.orderId());
        }

        // when - 동시 결제 실행
        long startTime = System.nanoTime();

        for (int i = 0; i < concurrentCount; i++) {
            final Long orderId = orderIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long taskStart = System.nanoTime();

                    PaymentResponse response = orderService.processPayment(
                            orderId,
                            new PaymentRequest(null, 0)
                    );

                    long taskEnd = System.nanoTime();
                    synchronized (latencies) {
                        latencies.add((taskEnd - taskStart) / 1_000_000);
                    }

                    if ("COMPLETED".equals(response.paymentStatus())) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(60, TimeUnit.SECONDS);
        long syncEndTime = System.nanoTime();

        // Kafka Consumer가 모든 메시지를 처리할 때까지 대기
        String rankingKey = "ranking:daily:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            assertThat(score).isNotNull();
            assertThat(score.intValue()).isGreaterThanOrEqualTo(successCount.get());
        });
        long asyncEndTime = System.nanoTime();

        executor.shutdown();

        // 통계 계산
        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();
        List<Long> sortedLatencies = latencies.stream().sorted().toList();
        long p50 = sortedLatencies.get(sortedLatencies.size() / 2);
        long p99 = sortedLatencies.get((int) (sortedLatencies.size() * 0.99));

        double syncDurationMs = (syncEndTime - startTime) / 1_000_000.0;
        double totalDurationMs = (asyncEndTime - startTime) / 1_000_000.0;

        System.out.println("========================================");
        System.out.println("[Kafka] 동시 100건 결제 벤치마크");
        System.out.println("========================================");
        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
        System.out.println("동기 처리 시간: " + String.format("%.2f", syncDurationMs) + " ms");
        System.out.println("전체 처리 시간: " + String.format("%.2f", totalDurationMs) + " ms");
        System.out.println("---");
        System.out.println("평균 Latency: " + String.format("%.2f", stats.getAverage()) + " ms");
        System.out.println("최소 Latency: " + stats.getMin() + " ms");
        System.out.println("최대 Latency: " + stats.getMax() + " ms");
        System.out.println("P50 Latency: " + p50 + " ms");
        System.out.println("P99 Latency: " + p99 + " ms");
        System.out.println("========================================");
    }

    @Test
    @DisplayName("[Kafka] 순차 1000건 결제 TPS 측정")
    void benchmark_sequential1000Payments() {
        // given
        int totalCount = 1000;
        AtomicInteger successCount = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();

        // when
        long startTime = System.nanoTime();

        for (int i = 0; i < totalCount; i++) {
            String email = "kafkasequential" + System.currentTimeMillis() + "_" + i + "@test.com";
            User user = userRepository.save(new User(null, "카프카순차" + i, email, 100_000));

            OrderRequest orderRequest = new OrderRequest(
                    user.getId(),
                    List.of(new OrderRequest.OrderItemRequest(testProduct.getId(), 1)),
                    null,
                    0
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);

            long taskStart = System.nanoTime();
            try {
                PaymentResponse response = orderService.processPayment(
                        orderResponse.orderId(),
                        new PaymentRequest(null, 0)
                );
                if ("COMPLETED".equals(response.paymentStatus())) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                // 실패 처리
            }
            long taskEnd = System.nanoTime();
            latencies.add((taskEnd - taskStart) / 1_000_000);

            if ((i + 1) % 100 == 0) {
                System.out.println("진행중: " + (i + 1) + "/" + totalCount);
            }
        }

        long syncEndTime = System.nanoTime();

        // Kafka Consumer가 모든 메시지를 처리할 때까지 대기
        String rankingKey = "ranking:daily:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(rankingKey, testProduct.getId().toString());
            assertThat(score).isNotNull();
            assertThat(score.intValue()).isGreaterThanOrEqualTo(successCount.get());
        });
        long asyncEndTime = System.nanoTime();

        // 통계 계산
        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();
        List<Long> sortedLatencies = latencies.stream().sorted().toList();
        long p50 = sortedLatencies.get(sortedLatencies.size() / 2);
        long p99 = sortedLatencies.get((int) (sortedLatencies.size() * 0.99));

        double syncDurationSec = (syncEndTime - startTime) / 1_000_000_000.0;
        double totalDurationSec = (asyncEndTime - startTime) / 1_000_000_000.0;
        double tps = successCount.get() / syncDurationSec;

        System.out.println("========================================");
        System.out.println("[Kafka] 순차 1000건 결제 벤치마크");
        System.out.println("========================================");
        System.out.println("성공: " + successCount.get() + "/" + totalCount);
        System.out.println("동기 처리 시간: " + String.format("%.2f", syncDurationSec) + " sec");
        System.out.println("전체 처리 시간: " + String.format("%.2f", totalDurationSec) + " sec");
        System.out.println("TPS (동기 기준): " + String.format("%.2f", tps) + " /sec");
        System.out.println("---");
        System.out.println("평균 Latency: " + String.format("%.2f", stats.getAverage()) + " ms");
        System.out.println("P50 Latency: " + p50 + " ms");
        System.out.println("P99 Latency: " + p99 + " ms");
        System.out.println("========================================");
    }
}
