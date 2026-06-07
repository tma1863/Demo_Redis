package com.example.demo.feature.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.http.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.example.demo.common.bootstrap.DataSeeder;
import com.example.demo.common.cache.CacheNames;
import com.example.demo.feature.category.entity.Category;
import com.example.demo.feature.product.entity.Product;
import com.example.demo.feature.product.repository.ProductRepository;

/**
 * Automated proof for Battleground 3: the cold read for a product id hits
 * PostgreSQL exactly once, and every subsequent read of that hot id is served
 * from Redis without touching the repository.
 *
 * <p>Full {@code @SpringBootTest} so the real Redis-backed cache manager,
 * {@code @Cacheable} proxy, and MVC stack are exercised end-to-end — only the
 * data source boundary ({@link ProductRepository}) is mocked, which lets us
 * <em>count</em> database lookups. Because the value genuinely round-trips
 * through Redis, this also exercises the Jackson-3 cache serializer for the new
 * {@code ProductResponse} record.
 *
 * <p>Requires a live Postgres + Redis with the project's env vars exported
 * (same precondition as {@code DemoApplicationTests}).
 */
@SpringBootTest
class ProductCachingIntegrationTest {

    // Ids deliberately beyond Integer.MAX_VALUE so the JSON number nodes parse
    // as Long, matching the Long literals below (avoids the jsonPath
    // Integer-vs-Long matcher mismatch). Also far outside any seeded id range.
    private static final long PRODUCT_ID = 9_000_000_001L;
    private static final long CATEGORY_ID = 9_000_000_777L;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CacheManager cacheManager;

    // Built from the live web context in setUp(); Boot 4 split @AutoConfigureMockMvc
    // out of spring-boot-starter-test, so we wire MockMvc the classic way (needs
    // only spring-test, which is already on the test classpath).
    private MockMvc mockMvc;

    @MockitoBean
    private ProductRepository productRepository;

    /**
     * Neutralize the one-shot bulk seeder. Its {@code ApplicationReadyEvent}
     * listener would otherwise drive the now-mocked {@link ProductRepository} on
     * a fresh database; as a mock its {@code seed()} is a no-op, so the real
     * seeding logic never runs against the mock.
     */
    @MockitoBean
    @SuppressWarnings("unused")
    private DataSeeder dataSeeder;

    /** Build MockMvc and start every test from a cold cache for the id under test. */
    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();

        Cache products = cacheManager.getCache(CacheNames.PRODUCTS);
        if (products != null) {
            products.evict(PRODUCT_ID);
        }
    }

    @Test
    void concurrentReadsOfSameProductHitDatabaseExactlyOnce() throws Exception {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct()));

        // First request: cold cache -> must read through to the repository and
        // return the mapped DTO inside the common ApiResponse envelope.
        mockMvc.perform(get("/api/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product retrieved successfully"))
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.data.stockQuantity").value(42))
                .andExpect(jsonPath("$.data.categoryId").value(CATEGORY_ID));

        // Subsequent reads of the same hot id: must all be Redis cache hits.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/products/{id}", PRODUCT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(PRODUCT_ID));
        }

        // The whole point of Battleground 3: 6 reads, exactly 1 database lookup.
        verify(productRepository, times(1)).findById(PRODUCT_ID);
    }

    @Test
    void updateRefreshesCacheSoNextReadIsServedWithoutTouchingTheDatabase() throws Exception {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct()));
        // save() echoes back the (now-mutated) managed entity, as JPA would.
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // PUT writes through to the DB once (findById + save) and, via @CachePut,
        // stores the updated value under products::id.
        mockMvc.perform(put("/api/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Keyboard\",\"price\":199.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Updated Keyboard"))
                .andExpect(jsonPath("$.data.price").value(199.99));

        // Every subsequent read must be a cache hit returning the NEW value —
        // @CachePut kept the entry warm, so the read path never re-queries.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/products/{id}", PRODUCT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Updated Keyboard"))
                    .andExpect(jsonPath("$.data.price").value(199.99));
        }

        // The whole point of @CachePut: the write read the DB once, and the 5
        // following reads added zero further lookups.
        verify(productRepository, times(1)).findById(PRODUCT_ID);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void updateWithInvalidBodyReturns400AndNeverTouchesTheDatabase() throws Exception {
        // Blank name + negative price both violate the request DTO constraints.
        mockMvc.perform(put("/api/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Validation rejects the request at the controller boundary, so the
        // service/repository are never invoked.
        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void missingProductReturns404AndIsNotCached() throws Exception {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        // A not-found result throws ResourceNotFoundException, which the global
        // handler renders as a 404 in the ApiResponse envelope...
        mockMvc.perform(get("/api/products/{id}", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // ...and because @Cacheable never stores a thrown exception, the second
        // request must hit the repository again (no negative caching).
        mockMvc.perform(get("/api/products/{id}", PRODUCT_ID))
                .andExpect(status().isNotFound());

        verify(productRepository, times(2)).findById(PRODUCT_ID);
    }

    private static Product sampleProduct() {
        Category category = Category.builder()
                .name("Peripherals")
                .slug("peripherals")
                .build();
        category.setId(CATEGORY_ID);

        Product product = Product.builder()
                .name("Mechanical Keyboard")
                .price(new BigDecimal("129.99"))
                .stockQuantity(42)
                .description("Hot-swappable RGB")
                .category(category)
                .build();
        product.setId(PRODUCT_ID);
        return product;
    }
}
