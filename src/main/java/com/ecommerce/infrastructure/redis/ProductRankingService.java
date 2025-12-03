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

@Slf4j
@Service
public class ProductRankingService {

    private final ProductRankingRedisRepository rankingRedisRepository;
    private final ProductRepository productRepository;
    private final PopularProductRepository popularProductRepository;
    private final ProductRankingService self;

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

    public void recordSale(Long productId, int quantity) {
        try {
            rankingRedisRepository.recordSale(productId, quantity);
            log.debug("판매 기록 완료: productId={}, quantity={}", productId, quantity);
        } catch (Exception e) {
            log.warn("Redis 판매 기록 실패: productId={}, error={}", productId, e.getMessage());
        }
    }

    public List<ProductResponse> getTopProducts(int limit) {
        long version = rankingRedisRepository.getCurrentVersion();
        return self.getTopProductsByVersion(limit, version);
    }

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

    public long incrementVersion() {
        return rankingRedisRepository.incrementVersion();
    }

    public long getCurrentVersion() {
        return rankingRedisRepository.getCurrentVersion();
    }

    public void clearRanking() {
        rankingRedisRepository.clearAll();
    }
}
