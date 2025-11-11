package com.ecommerce.application.dto;

import com.ecommerce.domain.entity.Product;

public record ProductResponse(
    Long id,
    String name,
    String description,
    Integer price,
    Integer stockQuantity
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.getStockQuantity()
        );
    }
}
