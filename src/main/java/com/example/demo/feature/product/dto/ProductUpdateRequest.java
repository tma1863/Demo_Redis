package com.example.demo.feature.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Write payload for {@code PUT /api/products/{id}} — the cache-invalidation
 * battleground (Spec §IV.4). Scoped to the two mutable "core details" the spec
 * calls out, name and price; stock/description are out of scope for the demo.
 *
 * <p>Validated at the controller boundary so a malformed write is rejected with
 * a 400 before it can reach the cache layer.
 */
public record ProductUpdateRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than 0")
        BigDecimal price) {
}
