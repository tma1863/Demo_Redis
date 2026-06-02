package com.example.demo.features.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.demo.features.product.entity.Product;

/**
 * Standard CRUD for {@link Product}. {@link JpaSpecificationExecutor} is wired in
 * now (rather than speculative finder methods) so the upcoming advanced-filtering
 * battleground can compose type-safe {@code Specification}s without changing this
 * interface.
 */
public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
}
