package com.example.demo.feature.product.service;

import com.example.demo.feature.product.dto.ProductResponse;
import com.example.demo.feature.product.dto.ProductUpdateRequest;

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

    /**
     * Update a product's core details and keep the cache consistent. Writes
     * through to PostgreSQL, then refreshes the {@code products::id} cache entry
     * with the new value ({@code @CachePut}) so the hot read stays warm, and
     * evicts the trending aggregate, which may now be stale.
     *
     * @param id      product identifier
     * @param request new name/price
     * @return the updated product projection
     * @throws com.example.demo.common.exception.ResourceNotFoundException if no
     *         product exists for {@code id}
     */
    ProductResponse updateProduct(Long id, ProductUpdateRequest request);
}
