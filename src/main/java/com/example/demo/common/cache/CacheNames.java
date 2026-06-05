package com.example.demo.common.cache;

/**
 * Canonical Redis cache-bucket names, shared between the feature services that
 * declare {@code @Cacheable} and the {@link com.example.demo.config.RedisConfig}
 * that configures each bucket's TTL and serializer.
 *
 * <p>A bucket name is a domain contract (it is the Redis key prefix and must
 * match byte-for-byte across producer and config), so it lives in {@code common}
 * rather than forcing feature code to import the infra {@code @Configuration}
 * class just to read a {@code String}. TTLs and the value serializer stay in
 * {@code RedisConfig} — those are genuine infrastructure concerns.
 */
public final class CacheNames {

    /** Cache bucket for single-product detail reads (Battleground 3). */
    public static final String PRODUCTS = "products";

    /** Cache bucket for the trending-products analytics result. */
    public static final String TRENDING_PRODUCTS = "trending-products";

    private CacheNames() {
    }
}
