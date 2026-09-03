package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByKitchen(Kitchen kitchen);
    List<Product> findByAvailableTodayTrueOrderByCreatedAtDesc();
    List<Product> findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(Kitchen kitchen);
    List<Product> findByNameContainingIgnoreCase(String name);
    List<Product> findByKitchenId(Long kitchenId);
    List<Product> findByKitchenAndCreatedAtAfterOrderByCreatedAtDesc(Kitchen kitchen, LocalDateTime after);

    /**
     * Atomic inventory adjustment for the live stepper. Guards are enforced inside the
     * UPDATE itself so concurrent clicks can never lose an increment, go negative, or
     * exceed the advertised maximum. Returns 0 when the adjustment was rejected.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.remainingQuantity = p.remainingQuantity + :delta, " +
            "p.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE p.id = :productId AND p.remainingQuantity IS NOT NULL " +
            "AND p.remainingQuantity + :delta >= 0 " +
            "AND (p.maxQuantity IS NULL OR p.remainingQuantity + :delta <= p.maxQuantity)")
    int adjustRemainingQuantity(@Param("productId") Long productId, @Param("delta") int delta);
}
