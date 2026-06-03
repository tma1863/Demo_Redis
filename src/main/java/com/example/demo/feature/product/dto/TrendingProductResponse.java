package com.example.demo.feature.product.dto;

import java.math.BigDecimal;

/**
 * Flat, read-only projection for the "trending products" benchmark. Populated
 * directly by the JPQL constructor expression in
 * {@code OrderItemRepository#findTopTrending} — no entity is hydrated, so the
 * strictly-LAZY relations never trigger an N+1 or a {@code LazyInitializationException}.
 */
public record TrendingProductResponse(
        Long productId,
        String name,
        BigDecimal price,
        Long totalSold) {
}
