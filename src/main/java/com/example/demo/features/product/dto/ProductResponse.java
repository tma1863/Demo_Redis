package com.example.demo.features.product.dto;

import java.math.BigDecimal;

import com.example.demo.features.product.entity.Product;

/**
 * Read-only, flat projection of a single {@link Product} for the
 * high-concurrency detail-read battleground.
 *
 * <p>Serving a DTO (rather than the JPA entity) keeps the response stable and,
 * crucially, prevents Jackson from walking the entity's strictly-LAZY
 * {@code category} relation during serialization — which would otherwise fire an
 * unexpected SELECT (or a {@code LazyInitializationException} once the session
 * is closed). It is also the value cached in Redis: a flat record of scalars
 * round-trips cleanly through the Jackson-3 default-typed cache serializer.
 *
 * <p>Mapping is done manually via {@link #fromEntity(Product)} — no
 * ModelMapper/reflection — so the exact set of fields read off the entity is
 * explicit and predictable, guaranteeing zero surprise queries.
 */
public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer stockQuantity,
        String description,
        Long categoryId) {

    /**
     * Build a response from a managed (or detached) {@link Product}. Reads only
     * scalar columns plus the category's identifier.
     *
     * <p>Reading {@code category.getId()} on the LAZY proxy is deliberate and
     * safe: Hibernate keeps the foreign key on the proxy itself, so the
     * identifier resolves <em>without</em> initializing the proxy or issuing a
     * SELECT. No other relation field is touched, so the cold read stays a
     * single, predictable query.
     */
    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getDescription(),
                product.getCategory().getId());
    }
}
