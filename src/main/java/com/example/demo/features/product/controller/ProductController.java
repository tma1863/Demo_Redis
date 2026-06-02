package com.example.demo.features.product.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.features.product.dto.TrendingProductResponse;
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

    /**
     * {@code GET /api/products/trending} — top 10 trending products by units
     * sold. Served from Redis when warm, recomputed from PostgreSQL when cold.
     */
    @GetMapping("/trending")
    public ApiResponse<List<TrendingProductResponse>> getTrendingProducts() {
        List<TrendingProductResponse> trending = trendingProductService.getTrendingProducts();
        return ApiResponse.success("Top trending products retrieved successfully", trending);
    }
}
