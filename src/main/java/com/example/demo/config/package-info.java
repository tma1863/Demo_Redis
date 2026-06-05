/**
 * Framework-level configuration (Redis connection, cache manager, etc.). Kept
 * separate from the feature packages so the caching/infrastructure wiring stays
 * out of the domain code. This package holds <em>generic</em> framework/infra
 * beans; domain-coupled tuning (e.g. JDBC batching, which only works because of
 * the {@code BaseEntity} pooled SEQUENCE) sits beside its primitive in
 * {@code common.config} instead. See {@link com.example.demo.config.RedisConfig}.
 */
package com.example.demo.config;
