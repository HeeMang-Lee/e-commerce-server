package com.ecommerce.application.scheduler;

import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
import com.ecommerce.application.dto.PaymentRequest;
import com.ecommerce.application.service.OrderService;
import com.ecommerce.config.IntegrationTestSupport;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.entity.User;
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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("랭킹 동기화 배치 테스트")
class RankingSyncSchedulerTest extends IntegrationTestSupport {

    @Autowired
    private RankingSyncScheduler rankingSyncScheduler;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductRankingRedisRepository rankingRedisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private User testUser;
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        rankingRedisRepository.clearAll();

        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        testUser = userRepository.save(
                new User(null, "배치테스트유저", "batch_" + uniqueSuffix + "@test.com", 500_000)
        );
        product1 = productRepository.save(
                new Product(null, "배치상품1_" + uniqueSuffix, "설명", 10_000, 100, "전자기기")
        );
        product2 = productRepository.save(
                new Product(null, "배치상품2_" + uniqueSuffix, "설명", 20_000, 100, "전자기기")
        );
    }

    @Test
    @DisplayName("결제 완료된 주문 기반으로 랭킹이 동기화된다")
    void completedPayments_shouldSyncToRanking() {
        // given - 주문 생성 및 결제 완료
        OrderRequest orderRequest = new OrderRequest(
                testUser.getId(),
                List.of(
                        new OrderRequest.OrderItemRequest(product1.getId(), 3),
                        new OrderRequest.OrderItemRequest(product2.getId(), 5)
                ),
                null, 0
        );
        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        orderService.processPayment(orderResponse.orderId(), new PaymentRequest(null, 0));

        // Redis 랭킹 초기화 (이벤트로 기록된 것 삭제)
        rankingRedisRepository.clearAll();

        // when - 배치 실행
        rankingSyncScheduler.syncRankingFromCompletedPayments();

        // then - 랭킹 복구 확인
        String rankingKey = "ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        Double score1 = redisTemplate.opsForZSet().score(rankingKey, product1.getId().toString());
        Double score2 = redisTemplate.opsForZSet().score(rankingKey, product2.getId().toString());

        assertThat(score1).isNotNull();
        assertThat(score2).isNotNull();
        // 배치는 누적이므로 기존 값 + 새 값이 될 수 있음
        assertThat(score1.intValue()).isGreaterThanOrEqualTo(3);
        assertThat(score2.intValue()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("여러 주문의 같은 상품 판매량이 합산되어 동기화된다")
    void multipleOrders_sameProduct_shouldAggregate() {
        // given - 여러 주문 생성 및 결제
        for (int i = 0; i < 3; i++) {
            OrderRequest orderRequest = new OrderRequest(
                    testUser.getId(),
                    List.of(new OrderRequest.OrderItemRequest(product1.getId(), 2)),
                    null, 0
            );
            OrderResponse orderResponse = orderService.createOrder(orderRequest);
            orderService.processPayment(orderResponse.orderId(), new PaymentRequest(null, 0));
        }

        // Redis 초기화
        rankingRedisRepository.clearAll();

        // when
        rankingSyncScheduler.syncRankingFromCompletedPayments();

        // then - 3건 × 2개 = 6개 이상 (누적)
        String rankingKey = "ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        Double score = redisTemplate.opsForZSet().score(rankingKey, product1.getId().toString());
        assertThat(score).isNotNull();
        assertThat(score.intValue()).isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("결제 완료 건이 없으면 동기화를 건너뛴다")
    void noCompletedPayments_shouldSkip() {
        // given - 결제 완료 건 없음, Redis도 비어있음
        rankingRedisRepository.clearAll();

        // when
        rankingSyncScheduler.syncRankingFromCompletedPayments();

        // then - 에러 없이 정상 종료
        String rankingKey = "ranking:daily:" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        Long size = redisTemplate.opsForZSet().zCard(rankingKey);
        // 기존 데이터가 없으므로 0 또는 null
        assertThat(size == null || size == 0).isTrue();
    }
}
