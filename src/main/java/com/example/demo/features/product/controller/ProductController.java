package com.example.demo.features.product.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.features.product.dto.ProductResponse;
import com.example.demo.features.product.dto.TrendingProductResponse;
import com.example.demo.features.product.service.ProductService;
import com.example.demo.features.product.service.TrendingProductService;

import lombok.RequiredArgsConstructor;

/**
 * HTTP entry point for the product feature. Thin by design: it delegates to the
 * service (where caching lives) and wraps the result in the common
 * {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final TrendingProductService trendingProductService;
    private final ProductService productService;

    /**
     * {@code GET /api/products/trending} — top 10 trending products by units
     * sold. Served from Redis when warm, recomputed from PostgreSQL when cold.
     */
    @GetMapping("/trending")
    public ApiResponse<List<TrendingProductResponse>> getTrendingProducts() {
        List<TrendingProductResponse> trending = trendingProductService.getTrendingProducts();
        return ApiResponse.success("Top trending products retrieved successfully", trending);
    }

    /**
     * {@code GET /api/products/{id}} — single product detail. The first hit for
     * an id reads through to PostgreSQL and warms Redis; concurrent and
     * subsequent hits are served entirely from the cache (Battleground 3).
     *
     * @param id product identifier from the path
     * @return the product wrapped in the common {@link ApiResponse} envelope
     */
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProductById(@PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ApiResponse.success("Product retrieved successfully", product);
    }
}
