package com.example.demo.feature.product.service;

import java.util.List;

import com.example.demo.feature.product.dto.TrendingProductResponse;

public interface TrendingProductService {

    /**
     * Top-N products by total units sold. The cold call hits PostgreSQL once and
     * populates Redis; warm calls within the TTL are served entirely from Redis.
     *
     * @return the top trending product projections
     */
    List<TrendingProductResponse> getTrendingProducts();
}
