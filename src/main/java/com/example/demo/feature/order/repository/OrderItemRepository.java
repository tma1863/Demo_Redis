package com.example.demo.feature.order.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.demo.feature.order.entity.OrderItem;
import com.example.demo.feature.product.dto.TrendingProductResponse;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * "Trending products" battleground: aggregate total units sold per product
     * entirely in the database ({@code SUM} + {@code GROUP BY}) and project the
     * result straight into a flat DTO. No entities are loaded, so the LAZY
     * associations never fire and there is no N+1.
     *
     * <p>JPQL has no {@code LIMIT}; the top-N cut is supplied by {@link Pageable}.
     * Call with {@code PageRequest.of(0, 10)} for the top 10.
     */
    @Query("""
            SELECT new com.example.demo.feature.product.dto.TrendingProductResponse(
                       p.id, p.name, p.price, SUM(oi.quantity))
            FROM OrderItem oi
            JOIN oi.product p
            GROUP BY p.id, p.name, p.price
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<TrendingProductResponse> findTopTrending(Pageable pageable);
}
