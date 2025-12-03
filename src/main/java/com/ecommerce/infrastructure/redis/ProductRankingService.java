package com.ecommerce.infrastructure.redis;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.PopularProductRepository;
import com.ecommerce.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 랭킹 서비스
 *
 * Redis Sorted Set을 활용한 실시간 판매 랭킹 관리
 *
 * 주요 기능:
 * 1. 판매 기록: 주문 완료 시 Redis에 판매량 증가 (ZINCRBY)
 * 2. 랭킹 조회: 3일간 판매량 기준 Top N 조회
 * 3. Fallback: Redis 데이터 없을 시 DB 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRankingService {

    private final ProductRankingRedisRepository rankingRedisRepository;
    private final ProductRepository productRepository;
    private final PopularProductRepository popularProductRepository;

    /**
     * 상품 판매 기록 (주문 완료 시 호출)
     *
     * @param productId 상품 ID
     * @param quantity 판매 수량
     */
    public void recordSale(Long productId, int quantity) {
        try {
            rankingRedisRepository.recordSale(productId, quantity);
            log.debug("판매 기록 완료: productId={}, quantity={}", productId, quantity);
        } catch (Exception e) {
            // Redis 실패해도 주문은 계속 진행
            log.warn("Redis 판매 기록 실패: productId={}, error={}", productId, e.getMessage());
        }
    }

    /**
     * 최근 3일간 인기 상품 Top N 조회
     *
     * 조회 전략:
     * 1. Redis Sorted Set에서 조회 시도
     * 2. Redis 데이터 없으면 DB Fallback
     *
     * @param limit 조회할 상품 수
     * @return 인기 상품 목록
     */
    public List<ProductResponse> getTopProducts(int limit) {
        try {
            // 1. Redis에서 조회
            List<Long> productIds = rankingRedisRepository.getTopProductsLast3Days(limit);

            if (!productIds.isEmpty()) {
                log.debug("Redis에서 인기 상품 조회: {} 건", productIds.size());
                return productIds.stream()
                        .map(id -> {
                            try {
                                Product product = productRepository.getByIdOrThrow(id);
                                return ProductResponse.from(product);
                            } catch (Exception e) {
                                log.warn("상품 조회 실패: productId={}", id);
                                return null;
                            }
                        })
                        .filter(p -> p != null)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Redis 랭킹 조회 실패, DB Fallback: error={}", e.getMessage());
        }

        // 2. Fallback: DB 조회
        return getTopProductsFromDB(limit);
    }

    /**
     * DB에서 인기 상품 조회 (Fallback)
     */
    private List<ProductResponse> getTopProductsFromDB(int limit) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(3);

        List<Long> topProductIds = popularProductRepository.getTopProductIds(startTime, endTime, limit);

        return topProductIds.stream()
                .map(id -> {
                    Product product = productRepository.getByIdOrThrow(id);
                    return ProductResponse.from(product);
                })
                .toList();
    }

    /**
     * 랭킹 데이터 초기화 (테스트용)
     */
    public void clearRanking() {
        rankingRedisRepository.clearAll();
    }
}
