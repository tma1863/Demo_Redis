package com.example.demo.common.bootstrap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.feature.category.entity.Category;
import com.example.demo.feature.category.repository.CategoryRepository;
import com.example.demo.feature.order.entity.OrderItem;
import com.example.demo.feature.order.repository.OrderItemRepository;
import com.example.demo.feature.product.entity.Product;
import com.example.demo.feature.product.repository.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-shot benchmark data loader. Bulk-inserts a large, deterministic dataset so
 * the un-cached "trending products" aggregation is genuinely slow — the baseline
 * we later beat with Redis.
 *
 * <p>High throughput depends on three things working together:
 * <ul>
 *   <li>{@link com.example.demo.common.config.HibernateBatchConfig} — JDBC
 *       batching + insert ordering;</li>
 *   <li>{@code BaseEntity}'s pooled SEQUENCE id generator (IDENTITY would defeat
 *       batching);</li>
 *   <li>the chunked {@code saveAll} + {@code flush}/{@code clear} below, which
 *       bounds the persistence context and triggers a batched flush per chunk.</li>
 * </ul>
 *
 * <p>Parent references are set via {@link EntityManager#getReference} so no parent
 * rows are ever read back — consistent with the project's strictly-LAZY policy.
 * Runs on {@link ApplicationReadyEvent} (after the schema is created) and is
 * idempotent: it skips entirely if any categories already exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder {

    private static final int CATEGORY_COUNT = 20;
    private static final int PRODUCT_COUNT = 1_000;
    private static final int ORDER_ITEM_COUNT = 50_000;
    private static final int BATCH_SIZE = 1_000;
    private static final long RANDOM_SEED = 42L;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        long existing = categoryRepository.count();
        if (existing > 0) {
            log.info("DataSeeder: {} categories already present — skipping seed.", existing);
            return;
        }

        long start = System.currentTimeMillis();
        log.info("DataSeeder: seeding {} categories, {} products, {} order items…",
                CATEGORY_COUNT, PRODUCT_COUNT, ORDER_ITEM_COUNT);

        Random random = new Random(RANDOM_SEED);
        List<Long> categoryIds = seedCategories();
        List<Long> productIds = seedProducts(categoryIds, random);
        seedOrderItems(productIds, random);

        log.info("DataSeeder: done in {} ms — {} categories / {} products / {} order items.",
                System.currentTimeMillis() - start,
                categoryRepository.count(), productRepository.count(),
                orderItemRepository.count());
    }

    private List<Long> seedCategories() {
        List<Category> categories = new ArrayList<>(CATEGORY_COUNT);
        for (int i = 1; i <= CATEGORY_COUNT; i++) {
            categories.add(Category.builder()
                    .name("Category " + i)
                    .slug("category-" + i)
                    .build());
        }
        List<Long> ids = flushChunk(categoryRepository, categories).stream()
                .map(Category::getId)
                .toList();
        return ids;
    }

    private List<Long> seedProducts(List<Long> categoryIds, Random random) {
        List<Long> productIds = new ArrayList<>(PRODUCT_COUNT);
        List<Product> chunk = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            Long categoryId = categoryIds.get(random.nextInt(categoryIds.size()));
            chunk.add(Product.builder()
                    .name("Product " + i)
                    // 1.00 .. 999.99
                    .price(BigDecimal.valueOf(100 + random.nextInt(99_900), 2))
                    .stockQuantity(random.nextInt(1_000))
                    .description("Benchmark product #" + i)
                    .category(entityManager.getReference(Category.class, categoryId))
                    .build());
            if (chunk.size() == BATCH_SIZE || i == PRODUCT_COUNT) {
                flushChunk(productRepository, chunk).forEach(p -> productIds.add(p.getId()));
                chunk.clear();
            }
        }
        return productIds;
    }

    private void seedOrderItems(List<Long> productIds, Random random) {
        List<OrderItem> chunk = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= ORDER_ITEM_COUNT; i++) {
            Long productId = productIds.get(random.nextInt(productIds.size()));
            chunk.add(OrderItem.builder()
                    .product(entityManager.getReference(Product.class, productId))
                    .quantity(1 + random.nextInt(10)) // 1 .. 10
                    .build());
            if (chunk.size() == BATCH_SIZE || i == ORDER_ITEM_COUNT) {
                flushChunk(orderItemRepository, chunk);
                chunk.clear();
            }
        }
    }

    /**
     * Persists a chunk, flushes it as a JDBC batch, then clears the persistence
     * context so it never grows past one chunk. Returned entities are detached but
     * carry their generated ids. {@code getReference} proxies created for the next
     * chunk are re-acquired after each clear.
     */
    private <T> List<T> flushChunk(JpaRepository<T, Long> repository, List<T> chunk) {
        List<T> saved = repository.saveAll(chunk);
        entityManager.flush();
        entityManager.clear();
        return saved;
    }
}
