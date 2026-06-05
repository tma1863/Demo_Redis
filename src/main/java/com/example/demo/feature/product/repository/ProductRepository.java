package com.example.demo.feature.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.feature.product.entity.Product;

/**
 * Standard CRUD for {@link Product}.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
