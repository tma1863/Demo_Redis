package com.example.demo.common.config;

import java.util.Map;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Hibernate JDBC batching in code. These tuning knobs live in
 * {@code common.config} (rather than in application.properties or the framework
 * {@code config} package) because they are domain-coupled, not generic infra:
 * JDBC batching is meaningless under an IDENTITY id generator, so this config is
 * co-located with the {@code BaseEntity} pooled-SEQUENCE change that makes it
 * work and the {@code DataSeeder} bulk load it accelerates — keeping that
 * dependency visible beside its primitive.
 *
 * <p>{@code batch_size} groups inserts/updates into JDBC batches; {@code order_*}
 * sorts statements by table so consecutive same-table rows actually batch
 * together. None of this engages under an IDENTITY id generator — it only works
 * because {@code BaseEntity} uses a pooled SEQUENCE.
 */
@Configuration
public class HibernateBatchConfig implements HibernatePropertiesCustomizer {

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.jdbc.batch_size", "1000");
        hibernateProperties.put("hibernate.order_inserts", "true");
        hibernateProperties.put("hibernate.order_updates", "true");
    }
}
