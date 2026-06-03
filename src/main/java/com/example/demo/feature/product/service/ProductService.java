package com.example.demo.feature.product.service;

import com.example.demo.feature.product.dto.ProductResponse;

public interface ProductService {

    /**
     * Fetch a single product by id. The cold call hits PostgreSQL once and
     * populates Redis; warm calls are served entirely from Redis.
     *
     * @param id product identifier
     * @return the product projection
     * @throws com.example.demo.common.exception.ResourceNotFoundException if no
     *         product exists for {@code id}
     */
    ProductResponse getProductById(Long id);
}
