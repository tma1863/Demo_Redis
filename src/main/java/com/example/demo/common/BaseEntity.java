package com.example.demo.common;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.SequenceGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Mapped superclass providing the shared pooled-SEQUENCE primary key for all entities. */
@MappedSuperclass
@Getter
@Setter
@ToString
public abstract class BaseEntity {

    /**
     * Shared, pooled SEQUENCE generator. SEQUENCE (not IDENTITY) is what lets
     * Hibernate JDBC-batch inserts — under IDENTITY the bulk seed would degrade
     * to one round-trip per row. {@code allocationSize} matches the seeder's
     * batch size so id blocks are reserved in bulk rather than per insert.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entity_id_seq")
    @SequenceGenerator(name = "entity_id_seq", sequenceName = "entity_id_seq", allocationSize = 1000)
    private Long id;
}
