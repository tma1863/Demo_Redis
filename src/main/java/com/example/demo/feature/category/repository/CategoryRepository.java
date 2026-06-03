package com.example.demo.feature.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.feature.category.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
