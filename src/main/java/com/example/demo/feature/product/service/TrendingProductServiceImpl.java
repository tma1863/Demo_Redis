package com.example.demo.feature.product.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.example.demo.common.cache.CacheNames;
import com.example.demo.feature.order.repository.OrderItemRepository;
import com.example.demo.feature.product.dto.TrendingProductResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read side of the "trending products" battleground. The expensive part — the
 * {@code SUM}/{@code GROUP BY} aggregation over the full {@code order_items}
 * table — runs entirely in PostgreSQL and projects straight into a flat DTO, so
 * no entities are hydrated and the strictly-LAZY associations never fire.
 *
 * <p>The result is cached in Redis: the first (cold) call computes the
 * aggregation and stores it; subsequent calls within the TTL are served from
 * memory without touching the database. The 5-minute TTL is configured for the
 * {@link CacheNames#TRENDING_PRODUCTS} bucket in {@link com.example.demo.config.RedisConfig}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingProductServiceImpl implements TrendingProductService {

    /** Top-N cut applied via {@code Pageable} (JPQL has no {@code LIMIT}). */
    private static final int TRENDING_LIMIT = 10;

    private final OrderItemRepository orderItemRepository;

    /**
     * Top {@value #TRENDING_LIMIT} products by total units sold. Annotated
     * {@code @Cacheable} so only the cold read hits PostgreSQL; warm reads are
     * served from Redis. The cache key is the method name ({@code getTrendingProducts}),
     * keyed under the {@code trending-products} bucket.
     */
    @Override
    @Cacheable(cacheNames = CacheNames.TRENDING_PRODUCTS, key = "#root.methodName")
    public List<TrendingProductResponse> getTrendingProducts() {
        log.info("Cache miss — aggregating top {} trending products from PostgreSQL", TRENDING_LIMIT);
        return orderItemRepository.findTopTrending(PageRequest.of(0, TRENDING_LIMIT));
    }
}
