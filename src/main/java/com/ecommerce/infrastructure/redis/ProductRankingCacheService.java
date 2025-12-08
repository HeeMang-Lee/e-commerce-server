package com.ecommerce.infrastructure.redis;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.config.CaffeineCacheConfig;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRankingCacheService {

    private final ProductRankingRedisRepository rankingRedisRepository;
    private final ProductRepository productRepository;

    @Cacheable(value = CaffeineCacheConfig.RANKING_CACHE, key = "#limit + '_' + #version")
    public List<ProductResponse> getTopProductsByVersion(int limit, long version) {
        log.debug("캐시 미스 - Redis 조회: limit={}, version={}", limit, version);

        List<Long> productIds = rankingRedisRepository.getTopProductsLast3Days(limit);

        if (productIds.isEmpty()) {
            return List.of();
        }

        log.debug("Redis에서 인기 상품 조회: {} 건", productIds.size());

        List<Product> products = productRepository.findAllById(productIds);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return productIds.stream()
                .map(productMap::get)
                .filter(p -> p != null)
                .map(ProductResponse::from)
                .toList();
    }
}
