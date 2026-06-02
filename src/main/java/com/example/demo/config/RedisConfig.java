package com.example.demo.config;

import java.time.Duration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Cache wiring for the Redis-backed {@code @Cacheable} layer. Lives in the
 * dedicated {@code config} package so the caching/infrastructure plumbing stays
 * out of the feature (domain) code.
 *
 * <p>Values are stored as human-readable JSON via
 * {@link GenericJacksonJsonRedisSerializer} — the Jackson 3 generic serializer
 * that ships with Spring Data Redis 4 (Spring Boot 4 resolves Jackson 3, so the
 * legacy {@code GenericJackson2JsonRedisSerializer} would fail at runtime for
 * lack of Jackson 2 databind). Default typing is enabled so polymorphic values
 * such as {@code List<TrendingProductResponse>} round-trip back to their
 * concrete types instead of degrading to {@code LinkedHashMap}. This is safe
 * here because the cache is written exclusively by this application's own code —
 * never from untrusted input.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /** Cache bucket for the trending-products analytics result. */
    public static final String TRENDING_PRODUCTS_CACHE = "trending-products";

    private static final Duration TRENDING_PRODUCTS_TTL = Duration.ofMinutes(5);

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default key serializer is already StringRedisSerializer -> readable keys.
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(SerializationPair.fromSerializer(cacheValueSerializer()));

        RedisCacheConfiguration trendingProducts = defaults.entryTtl(TRENDING_PRODUCTS_TTL);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(TRENDING_PRODUCTS_CACHE, trendingProducts)
                .build();
    }

    /**
     * The JSON value serializer used for every cache bucket. Extracted (and
     * package-visible) so the round-trip behaviour can be unit-tested without
     * standing up Redis. Default typing must stay enabled, otherwise polymorphic
     * values deserialize back to {@code LinkedHashMap} instead of their concrete
     * type.
     */
    static GenericJacksonJsonRedisSerializer cacheValueSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .enableSpringCacheNullValueSupport()
                .build();
    }
}
