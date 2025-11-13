package com.ecommerce.application.service;

import com.ecommerce.application.dto.OrderHistoryResponse;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N+1 문제 해결 검증 테스트
 * Hibernate Statistics를 사용하여 실제 쿼리 수를 측정합니다
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceNPlusOneTest {

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
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private User testUser;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // Hibernate Statistics 활성화
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // 테스트 데이터 생성
        testUser = new User(null, "테스트사용자", "test@example.com", 100000);
        userRepository.save(testUser);

        testProduct1 = new Product(null, "상품1", "설명1", 10000, 100, "전자제품");
        testProduct2 = new Product(null, "상품2", "설명2", 20000, 100, "전자제품");
        productRepository.save(testProduct1);
        productRepository.save(testProduct2);
    }

    @AfterEach
    void tearDown() {
        // 외래 키 제약 조건 때문에 순서가 중요합니다
        try {
            orderItemRepository.deleteAll();
            orderRepository.deleteAll();
            productRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // 테스트 실패 시 정리 실패는 무시
        }
    }

    @Test
    @DisplayName("N+1 문제 해결 검증: 10개 주문 조회 시 쿼리 수가 2개여야 함")
    void getOrderHistory_should_not_have_n_plus_one_problem() {
        // given: 10개의 주문 생성
        int orderCount = 10;
        createOrders(testUser.getId(), orderCount);

        // 통계 초기화 (setup 과정 제외)
        statistics.clear();

        // when: 주문 내역 조회
        List<OrderHistoryResponse> result = orderService.getOrderHistory(testUser.getId());

        // then: 결과 검증
        assertThat(result).hasSize(orderCount);

        // 쿼리 수 검증
        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("실행된 쿼리 수: " + queryCount);
        System.out.println("Entity 로드 수: " + statistics.getEntityLoadCount());
        System.out.println("Collection 로드 수: " + statistics.getCollectionLoadCount());

        // N+1 문제가 해결되었다면:
        // 1개: SELECT * FROM orders WHERE user_id = ?
        // 1개: SELECT * FROM order_items WHERE order_id IN (?, ?, ..., ?)
        // 총 2개의 쿼리만 실행되어야 함
        assertThat(queryCount).isLessThanOrEqualTo(2)
                .as("N+1 문제가 해결되어 2개 이하의 쿼리만 실행되어야 합니다");
    }

    @Test
    @DisplayName("N+1 문제 해결 검증: 100개 주문 조회 시에도 쿼리 수가 2개여야 함")
    void getOrderHistory_with_100_orders_should_execute_only_2_queries() {
        // given: 100개의 주문 생성
        int orderCount = 100;
        createOrders(testUser.getId(), orderCount);

        // 통계 초기화
        statistics.clear();

        // when: 주문 내역 조회
        long startTime = System.currentTimeMillis();
        List<OrderHistoryResponse> result = orderService.getOrderHistory(testUser.getId());
        long endTime = System.currentTimeMillis();

        // then
        assertThat(result).hasSize(orderCount);

        long queryCount = statistics.getPrepareStatementCount();
        long duration = endTime - startTime;

        System.out.println("=== 성능 테스트 결과 (100개 주문) ===");
        System.out.println("실행된 쿼리 수: " + queryCount);
        System.out.println("실행 시간: " + duration + "ms");
        System.out.println("Entity 로드 수: " + statistics.getEntityLoadCount());

        // 주문 개수와 무관하게 2개의 쿼리만 실행되어야 함
        assertThat(queryCount).isLessThanOrEqualTo(2)
                .as("주문 개수와 무관하게 2개 이하의 쿼리만 실행되어야 합니다");

        // 성능 기준: 100개 주문 조회가 500ms 이내에 완료되어야 함
        assertThat(duration).isLessThan(500)
                .as("100개 주문 조회가 500ms 이내에 완료되어야 합니다");
    }

    @Test
    @DisplayName("빈 주문 내역 조회 시 쿼리가 1개만 실행되어야 함")
    void getOrderHistory_with_no_orders_should_execute_only_1_query() {
        // given: 주문이 없는 사용자
        User emptyUser = new User(null, "빈사용자", "empty@example.com", 0);
        userRepository.save(emptyUser);

        // 통계 초기화
        statistics.clear();

        // when
        List<OrderHistoryResponse> result = orderService.getOrderHistory(emptyUser.getId());

        // then
        assertThat(result).isEmpty();

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("실행된 쿼리 수 (빈 결과): " + queryCount);

        // 주문이 없으므로 order_items 조회 쿼리는 실행되지 않아야 함
        assertThat(queryCount).isEqualTo(1)
                .as("빈 결과인 경우 orders 조회 쿼리 1개만 실행되어야 합니다");
    }

    /**
     * 테스트용 주문 데이터 생성
     */
    private void createOrders(Long userId, int count) {
        for (int i = 0; i < count; i++) {
            Order order = new Order(userId);
            orderRepository.save(order);

            // 각 주문에 2개의 아이템 추가
            OrderItem item1 = new OrderItem(testProduct1, 1);
            item1.setOrderId(order.getId());
            orderItemRepository.save(item1);

            OrderItem item2 = new OrderItem(testProduct2, 2);
            item2.setOrderId(order.getId());
            orderItemRepository.save(item2);
        }
    }
}
