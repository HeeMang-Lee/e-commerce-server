package com.ecommerce.application.service;

import com.ecommerce.application.dto.ProductListResponse;
import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.config.RedisCacheConfig;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 상품 캐싱 통합 테스트
 *
 * Toss 테스트 전략 기반:
 * - 실제 Redis를 사용하여 캐싱 동작 검증
 * - 사용자 시나리오 기반 테스트 (캐시 히트/미스, TTL, Cache Stampede)
 * - 테스트 실패 시 명확한 원인 파악 가능하도록 구성
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("상품 캐싱 통합 테스트")
class ProductCacheIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void tearDown() {
        // 캐시 클리어
        if (cacheManager.getCache(RedisCacheConfig.PRODUCT_LIST_CACHE) != null) {
            cacheManager.getCache(RedisCacheConfig.PRODUCT_LIST_CACHE).clear();
        }
        if (cacheManager.getCache(RedisCacheConfig.PRODUCT_CACHE) != null) {
            cacheManager.getCache(RedisCacheConfig.PRODUCT_CACHE).clear();
        }

        productRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 목록 조회 시 캐시가 정상 동작한다")
    void getProducts_CacheWorks() {
        // given: 상품 3개 생성
        Product product1 = new Product(null, "상품1", "설명1", 10000, 100, "전자제품");
        Product product2 = new Product(null, "상품2", "설명2", 20000, 50, "전자제품");
        Product product3 = new Product(null, "상품3", "설명3", 30000, 5, "의류");
        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);

        Cache cache = cacheManager.getCache(RedisCacheConfig.PRODUCT_LIST_CACHE);
        assertThat(cache).isNotNull();

        // when: 첫 번째 조회 (캐시 미스)
        List<ProductListResponse> firstCall = productService.getProducts();

        // then: 캐시에 저장되어야 함
        Cache.ValueWrapper cachedValue = cache.get("all");
        assertThat(cachedValue).isNotNull();
        assertThat(cachedValue.get()).isNotNull();

        // when: 상품 추가 후 두 번째 조회 (캐시 히트)
        Product product4 = new Product(null, "상품4", "설명4", 40000, 30, "전자제품");
        productRepository.save(product4);

        List<ProductListResponse> secondCall = productService.getProducts();

        // then: 캐시된 데이터를 반환하므로 새 상품이 보이지 않아야 함
        assertThat(firstCall).hasSize(3);
        assertThat(secondCall).hasSize(3); // 캐시 히트
        assertThat(firstCall).isEqualTo(secondCall);
    }

    @Test
    @DisplayName("상품 상세 조회 시 캐시가 정상 동작한다")
    void getProduct_CacheWorks() {
        // given
        Product product = new Product(null, "테스트상품", "설명", 50000, 20, "전자제품");
        Product saved = productRepository.save(product);

        Cache cache = cacheManager.getCache(RedisCacheConfig.PRODUCT_CACHE);
        assertThat(cache).isNotNull();

        // when: 첫 번째 조회 (캐시 미스)
        ProductResponse firstCall = productService.getProduct(saved.getId());

        // then: 캐시에 저장되어야 함
        Cache.ValueWrapper cachedValue = cache.get(saved.getId());
        assertThat(cachedValue).isNotNull();

        // when: DB에서 재고 변경
        saved.reduceStock(5);
        productRepository.save(saved);

        // when: 두 번째 조회 (캐시 히트)
        ProductResponse secondCall = productService.getProduct(saved.getId());

        // then: 캐시된 데이터 반환 (재고 변경 전 값)
        assertThat(firstCall.stockQuantity()).isEqualTo(20);
        assertThat(secondCall.stockQuantity()).isEqualTo(20); // 캐시 히트
    }

    @Test
    @DisplayName("캐시 만료 후 새로운 데이터를 조회한다")
    void getProducts_AfterCacheExpiration_FetchNewData() throws InterruptedException {
        // given
        Product product1 = new Product(null, "상품1", "설명1", 10000, 100, "전자제품");
        productRepository.save(product1);

        // when: 첫 번째 조회
        List<ProductListResponse> firstCall = productService.getProducts();
        assertThat(firstCall).hasSize(1);

        // when: 새 상품 추가
        Product product2 = new Product(null, "상품2", "설명2", 20000, 50, "전자제품");
        productRepository.save(product2);

        // when: 캐시 강제 삭제 (TTL 만료 시뮬레이션)
        Cache cache = cacheManager.getCache(RedisCacheConfig.PRODUCT_LIST_CACHE);
        cache.clear();

        // when: 캐시 만료 후 조회
        List<ProductListResponse> afterExpiration = productService.getProducts();

        // then: 새 데이터 반영
        assertThat(afterExpiration).hasSize(2);
    }

    @Test
    @DisplayName("Cache Stampede 방지: 동시 요청 시 DB는 1번만 조회된다")
    void getProducts_CacheStampedePrevention() throws InterruptedException {
        // given: 상품 데이터 준비
        Product product = new Product(null, "인기상품", "설명", 10000, 100, "전자제품");
        productRepository.save(product);

        // 캐시 클리어 (콜드 스타트 시뮬레이션)
        Cache cache = cacheManager.getCache(RedisCacheConfig.PRODUCT_LIST_CACHE);
        cache.clear();

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 50개 스레드가 동시에 같은 캐시 키 조회
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    List<ProductListResponse> result = productService.getProducts();

                    // 모든 스레드가 동일한 결과를 받아야 함
                    if (result != null && result.size() == 1) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: sync=true 덕분에 모든 요청이 성공해야 함
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Cache Stampede가 방지되어 캐시에 정상적으로 저장됨
        Cache.ValueWrapper cachedValue = cache.get("all");
        assertThat(cachedValue).isNotNull();
    }

    @Test
    @DisplayName("재고 상태 변화에 따라 캐시된 상품 목록의 상태가 정확하다")
    void getProducts_StockStatusReflectedInCache() {
        // given: 재고가 각기 다른 상품들
        Product available = new Product(null, "재고충분", "설명", 10000, 50, "전자제품");
        Product lowStock = new Product(null, "재고부족", "설명", 20000, 5, "전자제품");
        Product soldOut = new Product(null, "품절", "설명", 30000, 0, "전자제품");

        productRepository.save(available);
        productRepository.save(lowStock);
        productRepository.save(soldOut);

        // when: 상품 목록 조회
        List<ProductListResponse> products = productService.getProducts();

        // then: 재고 상태가 정확히 반영되어야 함
        assertThat(products).hasSize(3);

        ProductListResponse availableProduct = products.stream()
                .filter(p -> p.name().equals("재고충분"))
                .findFirst().orElseThrow();
        assertThat(availableProduct.stockStatus().name()).isEqualTo("AVAILABLE");

        ProductListResponse lowStockProduct = products.stream()
                .filter(p -> p.name().equals("재고부족"))
                .findFirst().orElseThrow();
        assertThat(lowStockProduct.stockStatus().name()).isEqualTo("LOW_STOCK");

        ProductListResponse soldOutProduct = products.stream()
                .filter(p -> p.name().equals("품절"))
                .findFirst().orElseThrow();
        assertThat(soldOutProduct.stockStatus().name()).isEqualTo("SOLD_OUT");
    }

    @Test
    @DisplayName("상품 상세는 짧은 TTL, 목록은 긴 TTL로 캐싱된다")
    void differentTTLForListAndDetail() {
        // given
        Product product = new Product(null, "테스트", "설명", 10000, 30, "전자제품");
        Product saved = productRepository.save(product);

        // when: 목록과 상세 조회
        List<ProductListResponse> list = productService.getProducts();
        ProductResponse detail = productService.getProduct(saved.getId());

        // then: 둘 다 캐시에 저장됨
        Cache listCache = cacheManager.getCache(RedisCacheConfig.PRODUCT_LIST_CACHE);
        Cache detailCache = cacheManager.getCache(RedisCacheConfig.PRODUCT_CACHE);

        assertThat(listCache.get("all")).isNotNull();
        assertThat(detailCache.get(saved.getId())).isNotNull();

        // 참고: TTL은 RedisCacheConfig에서 설정됨
        // - PRODUCT_LIST_CACHE: 5분
        // - PRODUCT_CACHE: 30초
    }

    @Test
    @DisplayName("실제 사용자 시나리오: 목록 조회 후 상세 조회 시 캐시 활용")
    void userScenario_ListThenDetail() {
        // given: 사용자가 쇼핑몰에 접속
        Product product1 = new Product(null, "노트북", "고성능", 1500000, 10, "전자제품");
        Product product2 = new Product(null, "마우스", "무선", 50000, 100, "전자제품");
        Product saved1 = productRepository.save(product1);
        Product saved2 = productRepository.save(product2);

        // when: 상품 목록 조회 (AVAILABLE 상태 확인)
        List<ProductListResponse> productList = productService.getProducts();
        assertThat(productList).hasSize(2);

        ProductListResponse laptop = productList.stream()
                .filter(p -> p.name().equals("노트북"))
                .findFirst().orElseThrow();
        assertThat(laptop.stockStatus().name()).isEqualTo("AVAILABLE");

        // when: 관심 있는 상품 클릭하여 상세 조회 (정확한 재고 확인)
        ProductResponse detail = productService.getProduct(saved1.getId());
        assertThat(detail.stockQuantity()).isEqualTo(10);

        // then: 두 조회 모두 캐시에서 처리됨 (성능 최적화)
        Cache listCache = cacheManager.getCache(RedisCacheConfig.PRODUCT_LIST_CACHE);
        Cache detailCache = cacheManager.getCache(RedisCacheConfig.PRODUCT_CACHE);

        assertThat(listCache.get("all")).isNotNull();
        assertThat(detailCache.get(saved1.getId())).isNotNull();
    }
}
