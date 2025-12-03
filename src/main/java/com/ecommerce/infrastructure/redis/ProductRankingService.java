package com.ecommerce.infrastructure.redis;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.config.CaffeineCacheConfig;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.PopularProductRepository;
import com.ecommerce.domain.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 랭킹 서비스
 *
 * 버전 기반 캐시 일관성 패턴 (올리브영 스타일):
 *
 * [조회 요청]
 *   → Redis에서 현재 버전 조회 (빠름, 숫자 하나)
 *   → 로컬 캐시에서 "ranking_v{버전}" 확인
 *   → 있으면 즉시 반환
 *   → 없으면 Redis 조회 → 로컬 캐시 저장 → 반환
 *
 * [판매 발생]
 *   → Redis ZINCRBY (실시간)
 *   → 버전은 그대로 (로컬 캐시 유지)
 *
 * [명시적 캐시 갱신 필요시]
 *   → incrementVersion() 호출
 *   → 다음 요청부터 새 버전으로 조회
 *
 * 장점:
 * - 매 요청마다 최신 버전 보장
 * - Pub/Sub 유실 걱정 없음
 * - 모든 서버가 동일한 데이터 제공
 */
@Slf4j
@Service
public class ProductRankingService {

    private final ProductRankingRedisRepository rankingRedisRepository;
    private final ProductRepository productRepository;
    private final PopularProductRepository popularProductRepository;
    private final ProductRankingService self;  // Self-injection for AOP proxy

    public ProductRankingService(
            ProductRankingRedisRepository rankingRedisRepository,
            ProductRepository productRepository,
            PopularProductRepository popularProductRepository,
            @Lazy ProductRankingService self) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.productRepository = productRepository;
        this.popularProductRepository = popularProductRepository;
        this.self = self;
    }

    /**
     * 상품 판매 기록 (주문 완료 시 호출)
     * Redis에 실시간 반영, 버전은 변경하지 않음
     *
     * @param productId 상품 ID
     * @param quantity 판매 수량
     */
    public void recordSale(Long productId, int quantity) {
        try {
            rankingRedisRepository.recordSale(productId, quantity);
            log.debug("판매 기록 완료: productId={}, quantity={}", productId, quantity);
        } catch (Exception e) {
            log.warn("Redis 판매 기록 실패: productId={}, error={}", productId, e.getMessage());
        }
    }

    /**
     * 최근 3일간 인기 상품 Top N 조회
     *
     * 버전 기반 캐시 조회:
     * 1. Redis에서 현재 버전 조회
     * 2. 로컬 캐시에서 해당 버전 데이터 확인
     * 3. 없으면 Redis 조회 후 로컬 캐시에 저장
     *
     * self-injection을 통해 AOP 프록시 경유하여 @Cacheable 동작
     *
     * @param limit 조회할 상품 수
     * @return 인기 상품 목록
     */
    public List<ProductResponse> getTopProducts(int limit) {
        long version = rankingRedisRepository.getCurrentVersion();
        return self.getTopProductsByVersion(limit, version);  // AOP 프록시 경유
    }

    /**
     * 버전 기반 캐시 조회
     * 캐시 키: "limit_version" (예: "5_3")
     */
    @Cacheable(value = CaffeineCacheConfig.RANKING_CACHE, key = "#limit + '_' + #version")
    public List<ProductResponse> getTopProductsByVersion(int limit, long version) {
        log.debug("캐시 미스 - Redis 조회: limit={}, version={}", limit, version);

        try {
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
     * 랭킹 버전 증가 (명시적 캐시 갱신)
     * 호출 시 모든 서버의 로컬 캐시가 자연스럽게 무효화됨
     *
     * @return 새 버전 번호
     */
    public long incrementVersion() {
        return rankingRedisRepository.incrementVersion();
    }

    /**
     * 현재 랭킹 버전 조회
     */
    public long getCurrentVersion() {
        return rankingRedisRepository.getCurrentVersion();
    }

    /**
     * 랭킹 데이터 초기화 (테스트용)
     */
    public void clearRanking() {
        rankingRedisRepository.clearAll();
    }
}
