package com.ecommerce.controller;

import com.ecommerce.api.ProductApi;
import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.ProductDto;
import com.ecommerce.dto.ResponseCode;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class ProductController implements ProductApi {

    @Override
    public ApiResponse<ProductDto.ListResponse> getProducts() {
        List<ProductDto.Response> products = Arrays.asList(
                new ProductDto.Response(1L, "무선 키보드", 89000, 100),
                new ProductDto.Response(2L, "무선 마우스", 45000, 150),
                new ProductDto.Response(3L, "모니터", 350000, 50),
                new ProductDto.Response(4L, "노트북 거치대", 25000, 200),
                new ProductDto.Response(5L, "USB 허브", 35000, 120)
        );

        ProductDto.ListResponse response = new ProductDto.ListResponse(products, products.size());
        return ApiResponse.of(ResponseCode.PRODUCT_SUCCESS, response);
    }

    @Override
    public ApiResponse<ProductDto.Response> getProduct(Long productId) {
        // 예시: 상품이 없는 경우 예외 발생
        // throw new BusinessException(ResponseCode.PRODUCT_NOT_FOUND);

        ProductDto.Response product = new ProductDto.Response(productId, "무선 키보드", 89000, 100);
        return ApiResponse.of(ResponseCode.PRODUCT_SUCCESS, product);
    }

    @Override
    public ApiResponse<ProductDto.ListResponse> getTopProducts() {
        List<ProductDto.Response> topProducts = Arrays.asList(
                new ProductDto.Response(2L, "무선 마우스", 45000, 150),
                new ProductDto.Response(1L, "무선 키보드", 89000, 130),
                new ProductDto.Response(5L, "USB 허브", 35000, 110),
                new ProductDto.Response(4L, "노트북 거치대", 25000, 95),
                new ProductDto.Response(3L, "모니터", 350000, 80)
        );

        ProductDto.ListResponse response = new ProductDto.ListResponse(topProducts, topProducts.size());
        return ApiResponse.of(ResponseCode.PRODUCT_SUCCESS, response, "최근 3일간 인기 상품 Top 5");
    }
}
