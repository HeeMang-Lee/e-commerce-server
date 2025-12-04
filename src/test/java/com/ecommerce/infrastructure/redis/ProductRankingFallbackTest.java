package com.ecommerce.infrastructure.redis;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.Order;
import com.ecommerce.domain.entity.OrderItem;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.OrderItemRepository;
import com.ecommerce.domain.repository.OrderRepository;
import com.ecommerce.domain.repository.ProductRepository;
import com.ecommerce.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("상품 랭킹 Fallback 테스트")
class ProductRankingFallbackTest {

    @Autowired
    private ProductRankingService productRankingService;

    @MockBean
    private ProductRankingCacheService cacheService;

    @MockBean
    private ProductRankingRedisRepository rankingRedisRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        product1 = productRepository.save(new Product(null, "상품1", "설명1", 10000, 100, "카테고리"));
        product2 = productRepository.save(new Product(null, "상품2", "설명2", 20000, 100, "카테고리"));
        product3 = productRepository.save(new Product(null, "상품3", "설명3", 30000, 100, "카테고리"));

        User user = userRepository.save(new User(null, "테스트유저", "test@test.com", 0));

        // product2: 50개, product3: 30개, product1: 10개 주문
        createOrderWithQuantity(user, product2, 50);
        createOrderWithQuantity(user, product3, 30);
        createOrderWithQuantity(user, product1, 10);
    }

    private void createOrderWithQuantity(User user, Product product, int quantity) {
        Order order = new Order(user.getId());
        order = orderRepository.save(order);

        OrderItem orderItem = new OrderItem(product, quantity);
        orderItem.setOrderId(order.getId());
        orderItemRepository.save(orderItem);
    }

    @Test
    @DisplayName("Redis 장애 시 DB에서 인기 상품을 조회한다")
    void getTopProducts_FallbackToDB_WhenRedisFailure() {
        // given - Redis 조회 시 예외 발생
        when(rankingRedisRepository.getCurrentVersion()).thenReturn(1L);
        when(cacheService.getTopProductsByVersion(anyInt(), anyLong()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        // when
        List<ProductResponse> topProducts = productRankingService.getTopProducts(5);

        // then - DB fallback으로 정상 조회
        assertThat(topProducts).isNotEmpty();
        assertThat(topProducts.get(0).id()).isEqualTo(product2.getId());  // 50개 - 1위
        assertThat(topProducts.get(1).id()).isEqualTo(product3.getId());  // 30개 - 2위
        assertThat(topProducts.get(2).id()).isEqualTo(product1.getId());  // 10개 - 3위
    }

    @Test
    @DisplayName("Redis 캐시가 비어있으면 DB에서 조회한다")
    void getTopProducts_FallbackToDB_WhenCacheEmpty() {
        // given - Redis 캐시 비어있음
        when(rankingRedisRepository.getCurrentVersion()).thenReturn(1L);
        when(cacheService.getTopProductsByVersion(anyInt(), anyLong()))
                .thenReturn(List.of());

        // when
        List<ProductResponse> topProducts = productRankingService.getTopProducts(5);

        // then - DB fallback으로 정상 조회
        assertThat(topProducts).hasSize(3);
        assertThat(topProducts.get(0).id()).isEqualTo(product2.getId());
    }

    @Test
    @DisplayName("Redis 버전 조회 실패해도 DB fallback이 동작한다")
    void getTopProducts_FallbackToDB_WhenVersionCheckFails() {
        // given - 버전 조회 시 예외 발생
        when(rankingRedisRepository.getCurrentVersion())
                .thenThrow(new RuntimeException("Redis connection failed"));

        // when
        List<ProductResponse> topProducts = productRankingService.getTopProducts(5);

        // then - 예외가 전파되지 않고 DB fallback 동작
        assertThat(topProducts).hasSize(3);
        assertThat(topProducts.get(0).id()).isEqualTo(product2.getId());  // 50개 - 1위
    }
}
