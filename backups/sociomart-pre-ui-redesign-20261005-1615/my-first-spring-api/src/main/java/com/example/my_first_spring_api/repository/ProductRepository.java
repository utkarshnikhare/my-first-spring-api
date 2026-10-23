package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
    List<Product> findByKitchenAndAvailableDateBeforeOrderByAvailableDateDescCreatedAtDesc(Kitchen kitchen, LocalDate date);

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

    /** Atomic stock restoration for a cancelled order; capped by the offering maximum. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.remainingQuantity = CASE "
            + "WHEN p.maxQuantity IS NULL THEN p.remainingQuantity + :qty "
            + "ELSE LEAST(p.maxQuantity, p.remainingQuantity + :qty) END, "
            + "p.bookedQuantity = CASE WHEN COALESCE(p.bookedQuantity, 0) - :qty < 0 THEN 0 "
            + "ELSE COALESCE(p.bookedQuantity, 0) - :qty END, "
            + "p.availableToday = CASE WHEN COALESCE(p.ordersPaused, false) = false "
            + "AND COALESCE(p.isPreorder, false) = false "
            + "AND (p.availableDate IS NULL OR p.availableDate <= CURRENT_DATE) "
            + "AND p.remainingQuantity + :qty > 0 THEN TRUE ELSE p.availableToday END, "
            + "p.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE p.id = :productId AND p.remainingQuantity IS NOT NULL")
    int restoreStock(@Param("productId") Long productId, @Param("qty") int qty);

    /** Reopen only an eligible same-day, unpaused, non-preorder offering after restoration. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.availableToday = true, p.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE p.id = :productId AND p.remainingQuantity > 0 "
            + "AND COALESCE(p.ordersPaused, false) = false "
            + "AND COALESCE(p.isPreorder, false) = false "
            + "AND (p.availableDate IS NULL OR p.availableDate <= CURRENT_DATE)")
    int reopenAfterRestore(@Param("productId") Long productId);

    /** Atomic decrement for unlimited offerings when a cancelled order is released. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.bookedQuantity = CASE WHEN COALESCE(p.bookedQuantity, 0) - :qty < 0 THEN 0 " +
            "ELSE COALESCE(p.bookedQuantity, 0) - :qty END, p.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE p.id = :productId AND p.remainingQuantity IS NULL")
    int decrementBookedQuantity(@Param("productId") Long productId, @Param("qty") int qty);

    /**
     * Atomic stock consumption at order placement. Decrements remaining and
     * increments booked in ONE UPDATE so concurrent checkouts cannot oversell.
     * Returns 0 when there is not enough stock left.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.remainingQuantity = p.remainingQuantity - :qty, " +
            "p.bookedQuantity = COALESCE(p.bookedQuantity, 0) + :qty, " +
            "p.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE p.id = :productId AND p.remainingQuantity IS NOT NULL " +
            "AND p.remainingQuantity >= :qty")
    int consumeStock(@Param("productId") Long productId,@Param("qty") int qty);
}
