package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.ProductResponse;
import com.ecommerce.application.service.ProductService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> getProducts() {
        return productService.getProducts();
    }

    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable @Positive(message = "상품 ID는 양수여야 합니다") Long productId) {
        return productService.getProduct(productId);
    }

    @GetMapping("/top")
    public List<ProductResponse> getTopProductsLast3Days() {
        return productService.getTopProductsLast3Days();
    }
}
