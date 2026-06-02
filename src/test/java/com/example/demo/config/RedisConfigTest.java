package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import com.example.demo.features.product.dto.TrendingProductResponse;

/**
 * Guards the one subtle decision in {@link RedisConfig}: the JSON cache
 * serializer must keep default typing enabled so a cached
 * {@code List<TrendingProductResponse>} deserializes back to its concrete record
 * type rather than degrading to {@code LinkedHashMap}. Pure serializer test — no
 * Spring context, no live Redis required.
 */
class RedisConfigTest {

    @Test
    void cacheValueSerializerRoundTripsListToConcreteRecordType() {
        GenericJacksonJsonRedisSerializer serializer = RedisConfig.cacheValueSerializer();

        // ArrayList mirrors what Spring Data returns from findTopTrending(...).
        List<TrendingProductResponse> original = new ArrayList<>(List.of(
                new TrendingProductResponse(1L, "Product 1", new BigDecimal("9.99"), 500L),
                new TrendingProductResponse(2L, "Product 2", new BigDecimal("19.95"), 320L)));

        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(List.class);
        List<?> restoredList = (List<?>) restored;
        assertThat(restoredList).hasSize(2);
        assertThat(restoredList.get(0))
                .isInstanceOf(TrendingProductResponse.class)
                .isEqualTo(original.get(0));
        assertThat(restoredList.get(1)).isEqualTo(original.get(1));
    }
}
