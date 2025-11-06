package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.application.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@DisplayName("ProductController 테스트")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("상품 목록을 조회한다")
    void getProducts() throws Exception {
        // given
        ProductResponse product1 = new ProductResponse(1L, "키보드", "무선", 50000, 10);
        ProductResponse product2 = new ProductResponse(2L, "마우스", "유선", 30000, 20);
        when(productService.getProducts()).thenReturn(Arrays.asList(product1, product2));

        // when & then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("키보드"))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].name").value("마우스"));
    }

    @Test
    @DisplayName("개별 상품을 조회한다")
    void getProduct() throws Exception {
        // given
        ProductResponse product = new ProductResponse(1L, "키보드", "무선", 50000, 10);
        when(productService.getProduct(1L)).thenReturn(product);

        // when & then
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("키보드"))
                .andExpect(jsonPath("$.price").value(50000))
                .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    @DisplayName("최근 3일간 인기 상품 Top 5를 조회한다")
    void getTopProductsLast3Days() throws Exception {
        // given
        ProductResponse product1 = new ProductResponse(1L, "키보드", "무선", 50000, 10);
        ProductResponse product2 = new ProductResponse(2L, "마우스", "유선", 30000, 20);
        when(productService.getTopProductsLast3Days()).thenReturn(Arrays.asList(product1, product2));

        // when & then
        mockMvc.perform(get("/api/products/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("키보드"))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].name").value("마우스"));
    }
}
