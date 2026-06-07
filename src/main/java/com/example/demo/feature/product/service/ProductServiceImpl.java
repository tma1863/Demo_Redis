package com.example.demo.feature.product.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.cache.CacheNames;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.feature.product.dto.ProductResponse;
import com.example.demo.feature.product.dto.ProductUpdateRequest;
import com.example.demo.feature.product.entity.Product;
import com.example.demo.feature.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    // sync=true is THE Battleground-3 mechanism: under a concurrent cold miss only one thread loads from PostgreSQL (no thundering herd).
    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id", sync = true)
    public ProductResponse getProductById(Long id) {
        log.info("Cache miss — loading product {} from PostgreSQL", id);
        return productRepository.findById(id)
                .map(ProductResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Override
    @Transactional
    // The cache-invalidation battleground. Two buckets, two consistency needs:
    //  - @CachePut refreshes products::id with the just-written value, so the hot read stays warm (no evict-then-cold-miss gap under load).
    //  - @CacheEvict(allEntries) drops the trending aggregate: a price change can reorder it, and the single fixed-key entry is cheap to recompute on next read.
    @Caching(
            put = @CachePut(cacheNames = CacheNames.PRODUCTS, key = "#id"),
            evict = @CacheEvict(cacheNames = CacheNames.TRENDING_PRODUCTS, allEntries = true))
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        log.info("Updating product {} and refreshing cache", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setName(request.name());
        product.setPrice(request.price());
        return ProductResponse.fromEntity(productRepository.save(product));
    }
}
