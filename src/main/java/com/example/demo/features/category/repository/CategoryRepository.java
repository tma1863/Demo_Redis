package com.example.demo.features.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.features.category.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
