package com.ecommerce.application.service;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.domain.entity.Order;
import com.ecommerce.domain.entity.OrderItem;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.PopularProductRepository;
import com.ecommerce.domain.repository.ProductRepository;
import com.ecommerce.infrastructure.repository.InMemoryPopularProductRepository;
import com.ecommerce.infrastructure.repository.InMemoryProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 상품 통합 테스트
 * 상품 조회, 목록 조회, 인기 상품 조회 기능을 통합적으로 검증합니다.
 */
@DisplayName("상품 통합 테스트")
class ProductIntegrationTest {

    private ProductRepository productRepository;
    private PopularProductRepository popularProductRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        popularProductRepository = new InMemoryPopularProductRepository();
        productService = new ProductService(productRepository, popularProductRepository);
    }

    @Test
    @DisplayName("상품 목록을 조회할 수 있다")
    void getProducts() {
        // given
        Product product1 = new Product(1L, "키보드", "무선 키보드", 89000, 10, "전자제품");
        Product product2 = new Product(2L, "마우스", "무선 마우스", 45000, 20, "전자제품");
        Product product3 = new Product(3L, "모니터", "27인치", 300000, 5, "전자제품");

        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);

        // when
        List<ProductResponse> products = productService.getProducts();

        // then
        assertThat(products).hasSize(3);
        assertThat(products).extracting("name")
                .containsExactlyInAnyOrder("키보드", "마우스", "모니터");
    }

    @Test
    @DisplayName("개별 상품을 조회할 수 있다")
    void getProduct() {
        // given
        Product product = new Product(1L, "키보드", "무선 키보드", 89000, 10, "전자제품");
        productRepository.save(product);

        // when
        ProductResponse response = productService.getProduct(1L);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("키보드");
        assertThat(response.description()).isEqualTo("무선 키보드");
        assertThat(response.price()).isEqualTo(89000);
        assertThat(response.stockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("존재하지 않는 상품을 조회하면 예외가 발생한다")
    void getProduct_NotFound_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("최근 3일간 가장 많이 팔린 상품 5개를 조회할 수 있다")
    void getTopProductsLast3Days() {
        // given
        Product product1 = new Product(1L, "상품1", "설명1", 10000, 100, "카테고리");
        Product product2 = new Product(2L, "상품2", "설명2", 20000, 100, "카테고리");
        Product product3 = new Product(3L, "상품3", "설명3", 30000, 100, "카테고리");
        Product product4 = new Product(4L, "상품4", "설명4", 40000, 100, "카테고리");
        Product product5 = new Product(5L, "상품5", "설명5", 50000, 100, "카테고리");
        Product product6 = new Product(6L, "상품6", "설명6", 60000, 100, "카테고리");

        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);
        productRepository.save(product4);
        productRepository.save(product5);
        productRepository.save(product6);

        LocalDateTime now = LocalDateTime.now();

        // 판매 기록 (최근 3일 이내)
        popularProductRepository.recordSale(1L, 50, now.minusDays(1));  // 1위
        popularProductRepository.recordSale(2L, 40, now.minusDays(1));  // 2위
        popularProductRepository.recordSale(3L, 30, now.minusDays(2));  // 3위
        popularProductRepository.recordSale(4L, 20, now.minusDays(2));  // 4위
        popularProductRepository.recordSale(5L, 10, now.minusDays(2));  // 5위
        popularProductRepository.recordSale(6L, 5, now.minusDays(2));   // 6위 (제외됨)

        // 3일 이전 판매는 제외됨
        popularProductRepository.recordSale(6L, 100, now.minusDays(4));

        // when
        List<ProductResponse> topProducts = productService.getTopProductsLast3Days();

        // then
        assertThat(topProducts).hasSize(5);
        assertThat(topProducts.get(0).name()).isEqualTo("상품1");  // 50개 판매
        assertThat(topProducts.get(1).name()).isEqualTo("상품2");  // 40개 판매
        assertThat(topProducts.get(2).name()).isEqualTo("상품3");  // 30개 판매
        assertThat(topProducts.get(3).name()).isEqualTo("상품4");  // 20개 판매
        assertThat(topProducts.get(4).name()).isEqualTo("상품5");  // 10개 판매
    }

    @Test
    @DisplayName("판매 이력이 없으면 빈 리스트가 반환된다")
    void getTopProductsLast3Days_NoSales_ReturnsEmptyList() {
        // given
        Product product = new Product(1L, "상품", "설명", 10000, 100, "카테고리");
        productRepository.save(product);

        // when
        List<ProductResponse> topProducts = productService.getTopProductsLast3Days();

        // then
        assertThat(topProducts).isEmpty();
    }

    @Test
    @DisplayName("3일 이전 판매 기록은 인기 상품에서 제외된다")
    void getTopProductsLast3Days_ExcludeOldSales() {
        // given
        Product product1 = new Product(1L, "상품1", "설명1", 10000, 100, "카테고리");
        Product product2 = new Product(2L, "상품2", "설명2", 20000, 100, "카테고리");

        productRepository.save(product1);
        productRepository.save(product2);

        LocalDateTime now = LocalDateTime.now();

        // 최근 판매
        popularProductRepository.recordSale(1L, 10, now.minusDays(1));

        // 3일 이전 판매 (제외됨)
        popularProductRepository.recordSale(2L, 100, now.minusDays(4));

        // when
        List<ProductResponse> topProducts = productService.getTopProductsLast3Days();

        // then
        assertThat(topProducts).hasSize(1);
        assertThat(topProducts.get(0).name()).isEqualTo("상품1");
    }

    @Test
    @DisplayName("동일 상품의 여러 판매가 누적되어 집계된다")
    void getTopProductsLast3Days_AccumulateSales() {
        // given
        Product product = new Product(1L, "상품", "설명", 10000, 100, "카테고리");
        productRepository.save(product);

        LocalDateTime now = LocalDateTime.now();

        // 여러 번 판매 기록
        popularProductRepository.recordSale(1L, 10, now.minusDays(1));
        popularProductRepository.recordSale(1L, 20, now.minusDays(1));
        popularProductRepository.recordSale(1L, 30, now.minusDays(2));

        // when
        List<ProductResponse> topProducts = productService.getTopProductsLast3Days();

        // then
        assertThat(topProducts).hasSize(1);
        // 10 + 20 + 30 = 60개가 누적됨
    }

    @Test
    @DisplayName("상품이 5개 미만이면 있는 만큼만 반환된다")
    void getTopProductsLast3Days_LessThan5Products() {
        // given
        Product product1 = new Product(1L, "상품1", "설명1", 10000, 100, "카테고리");
        Product product2 = new Product(2L, "상품2", "설명2", 20000, 100, "카테고리");

        productRepository.save(product1);
        productRepository.save(product2);

        LocalDateTime now = LocalDateTime.now();

        popularProductRepository.recordSale(1L, 10, now.minusDays(1));
        popularProductRepository.recordSale(2L, 5, now.minusDays(1));

        // when
        List<ProductResponse> topProducts = productService.getTopProductsLast3Days();

        // then
        assertThat(topProducts).hasSize(2);
    }
}
