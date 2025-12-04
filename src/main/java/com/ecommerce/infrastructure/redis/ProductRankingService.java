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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRankingService {

    private final ProductRankingRedisRepository rankingRedisRepository;
    private final ProductRankingCacheService cacheService;
    private final ProductRepository productRepository;
    private final PopularProductRepository popularProductRepository;

    public void recordSale(Long productId, int quantity) {
        try {
            rankingRedisRepository.recordSale(productId, quantity);
            log.debug("판매 기록 완료: productId={}, quantity={}", productId, quantity);
        } catch (Exception e) {
            log.warn("Redis 판매 기록 실패: productId={}, error={}", productId, e.getMessage());
        }
    }

    public List<ProductResponse> getTopProducts(int limit) {
        try {
            long version = rankingRedisRepository.getCurrentVersion();
            List<ProductResponse> cached = cacheService.getTopProductsByVersion(limit, version);
            if (!cached.isEmpty()) {
                return cached;
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

        if (topProductIds.isEmpty()) {
            return List.of();
        }

        List<Product> products = productRepository.findAllById(topProductIds);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return topProductIds.stream()
                .map(productMap::get)
                .filter(p -> p != null)
                .map(ProductResponse::from)
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
