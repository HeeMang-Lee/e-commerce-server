package com.ecommerce.infrastructure.redis;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.config.CaffeineCacheConfig;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.PopularProductRepository;
import com.ecommerce.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 랭킹 서비스
 *
 * Redis Sorted Set + Caffeine 로컬 캐시를 활용한 실시간 판매 랭킹 관리
 *
 * 캐시 전략:
 * 1. 로컬 캐시 (Caffeine): 빠른 응답, 60초 TTL
 * 2. Redis Sorted Set: 분산 환경 데이터 공유
 * 3. DB Fallback: Redis 장애 시 대체
 *
 * 캐시 무효화:
 * - Redis Pub/Sub로 모든 서버의 로컬 캐시 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRankingService {

    private final ProductRankingRedisRepository rankingRedisRepository;
    private final ProductRepository productRepository;
    private final PopularProductRepository popularProductRepository;
    private final RankingCacheInvalidator cacheInvalidator;

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
     * 1. Caffeine 로컬 캐시 확인 (가장 빠름)
     * 2. Redis Sorted Set에서 조회
     * 3. Redis 데이터 없으면 DB Fallback
     *
     * @param limit 조회할 상품 수
     * @return 인기 상품 목록
     */
    @Cacheable(value = CaffeineCacheConfig.RANKING_CACHE, key = "#limit")
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
        cacheInvalidator.publishInvalidation();
    }

    /**
     * 캐시 무효화 발행
     * 배치 처리 후 모든 서버의 로컬 캐시를 무효화할 때 사용
     */
    public void invalidateCache() {
        cacheInvalidator.publishInvalidation();
    }
}
