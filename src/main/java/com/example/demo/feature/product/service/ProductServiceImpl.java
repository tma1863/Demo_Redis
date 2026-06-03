package com.example.demo.feature.product.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.config.RedisConfig;
import com.example.demo.feature.product.dto.ProductResponse;
import com.example.demo.feature.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    @Cacheable(cacheNames = RedisConfig.PRODUCTS_CACHE, key = "#id", sync = true)
    public ProductResponse getProductById(Long id) {
        log.info("Cache miss — loading product {} from PostgreSQL", id);
        return productRepository.findById(id)
                .map(ProductResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
}
