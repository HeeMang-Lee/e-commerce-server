package com.ecommerce.application.service;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.domain.entity.Product;
import com.ecommerce.domain.repository.PopularProductRepository;
import com.ecommerce.domain.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 테스트")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PopularProductRepository popularProductRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품 목록을 조회한다")
    void getProducts() {
        // given
        Product product1 = new Product(1L, "키보드", "무선 키보드", 89000, 10, "전자제품");
        Product product2 = new Product(2L, "마우스", "무선 마우스", 45000, 20, "전자제품");
        when(productRepository.findAll()).thenReturn(Arrays.asList(product1, product2));

        // when
        List<ProductResponse> products = productService.getProducts();

        // then
        assertThat(products).hasSize(2);
        assertThat(products.get(0).getName()).isEqualTo("키보드");
        assertThat(products.get(1).getName()).isEqualTo("마우스");
    }

    @Test
    @DisplayName("상품을 단건 조회한다")
    void getProduct() {
        // given
        Product product = new Product(1L, "키보드", "무선 키보드", 89000, 10, "전자제품");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // when
        ProductResponse response = productService.getProduct(1L);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("키보드");
        assertThat(response.getPrice()).isEqualTo(89000);
        assertThat(response.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("존재하지 않는 상품은 조회할 수 없다")
    void getProduct_NotFound() {
        // given
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("최근 3일간 인기 상품 Top 5를 조회한다")
    void getTopProductsLast3Days() {
        // given
        Product product1 = new Product(1L, "키보드", "무선 키보드", 89000, 10, "전자제품");
        Product product2 = new Product(2L, "마우스", "무선 마우스", 45000, 20, "전자제품");
        Product product3 = new Product(3L, "모니터", "27인치", 300000, 5, "전자제품");

        when(popularProductRepository.getTopProductIds(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(Arrays.asList(1L, 2L, 3L));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product3));

        // when
        List<ProductResponse> topProducts = productService.getTopProductsLast3Days();

        // then
        assertThat(topProducts).hasSize(3);
        assertThat(topProducts.get(0).getName()).isEqualTo("키보드");
        assertThat(topProducts.get(1).getName()).isEqualTo("마우스");
        assertThat(topProducts.get(2).getName()).isEqualTo("모니터");
    }
}
