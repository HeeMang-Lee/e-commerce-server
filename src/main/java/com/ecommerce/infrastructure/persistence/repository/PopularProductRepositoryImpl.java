package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.repository.PopularProductRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PopularProductRepositoryImpl implements PopularProductRepository {

    private final EntityManager entityManager;

    @Override
    public List<Long> getTopProductIds(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        // View 대신 직접 JOIN 쿼리 사용 (Testcontainers 환경 호환)
        String sql = """
            SELECT oi.product_id
            FROM order_items oi
            INNER JOIN orders o ON oi.order_id = o.id
            WHERE o.created_at >= :startTime AND o.created_at < :endTime
            GROUP BY oi.product_id
            ORDER BY SUM(oi.quantity) DESC
            LIMIT :limit
            """;

        return entityManager.createNativeQuery(sql, Long.class)
                .setParameter("startTime", startTime)
                .setParameter("endTime", endTime)
                .setParameter("limit", limit)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteAll() {
        // View는 삭제할 수 없으므로, 테스트 목적으로 order_items와 orders 테이블을 정리
        entityManager.createNativeQuery("DELETE FROM order_items").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM orders").executeUpdate();
    }

    @Override
    @Transactional
    public void recordSale(Long productId, int quantity, LocalDateTime soldAt) {
        // 테스트용: 먼저 더미 주문 생성
        String createOrderSql = """
            INSERT INTO orders (user_id, order_number, created_at, updated_at)
            VALUES (1, CONCAT('TEST-', UUID()), :soldAt, :soldAt)
            """;

        entityManager.createNativeQuery(createOrderSql)
                .setParameter("soldAt", soldAt)
                .executeUpdate();

        // 생성된 주문 ID 가져오기
        Long orderId = ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();

        // 주문 아이템 삽입
        String sql = """
            INSERT INTO order_items (order_id, product_id, snapshot_product_name, snapshot_price, quantity, item_total_amount, status, created_at, updated_at)
            VALUES (:orderId, :productId, 'Test Product', 10000.00, :quantity, :totalAmount, 'PENDING', :soldAt, :soldAt)
            """;

        entityManager.createNativeQuery(sql)
                .setParameter("orderId", orderId)
                .setParameter("productId", productId)
                .setParameter("quantity", quantity)
                .setParameter("totalAmount", quantity * 10000.00)
                .setParameter("soldAt", soldAt)
                .executeUpdate();
    }
}
