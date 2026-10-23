package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    java.util.Optional<Order> findByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
    /** Full buyer/seller order read with items and offering snapshots initialized. */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);


    List<Order> findByBuyerOrderByCreatedAtDesc(User buyer);
    /**
     * Existence check used by order-number generation.
     *
     * <p>{@code orders.order_number} already carries a UNIQUE constraint, which is
     * the authoritative backstop. This lets the service avoid a collision in the
     * normal case instead of failing the insert and rolling the order back.
     */
    boolean existsByOrderNumber(String orderNumber);
    List<Order> findByKitchenOrderByCreatedAtDesc(Kitchen kitchen);
    List<Order> findByKitchenAndOrderStatusNotInOrderByCreatedAtDesc(Kitchen kitchen, List<OrderStatus> statuses);
    List<Order> findByKitchenAndCreatedAtBetweenOrderByCreatedAtDesc(Kitchen kitchen, LocalDateTime start, LocalDateTime end);
    long countByCreatedAtAfter(LocalDateTime after);
    long countByKitchen(Kitchen kitchen);

    /** Orders from a point in time with items fetched eagerly (avoids N+1 on aggregations). */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
            "WHERE o.kitchen = :kitchen AND o.createdAt >= :start " +
            "ORDER BY o.createdAt DESC")
    List<Order> findByKitchenAndCreatedAtAfterWithItems(@Param("kitchen") Kitchen kitchen,
                                                        @Param("start") LocalDateTime start);

    /** Orders in a date window with items fetched eagerly (avoids N+1 on aggregations). */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
            "WHERE o.kitchen = :kitchen AND o.createdAt >= :start AND o.createdAt < :end " +
            "ORDER BY o.createdAt DESC")
    List<Order> findByKitchenAndCreatedAtBetweenWithItems(@Param("kitchen") Kitchen kitchen,
                                                          @Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end);

    long countDistinctBuyerByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countDistinctSellerByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT o.buyer.id) FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
    long countDistinctBuyersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT k.seller.id) FROM Order o JOIN o.kitchen k WHERE o.createdAt >= :start AND o.createdAt < :end")
    long countDistinctSellersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT CAST(o.createdAt AS date), COUNT(DISTINCT o.buyer.id), COUNT(DISTINCT k.seller.id) " +
            "FROM Order o JOIN o.kitchen k " +
            "WHERE o.createdAt >= :start AND o.createdAt < :end " +
            "GROUP BY CAST(o.createdAt AS date) " +
            "ORDER BY CAST(o.createdAt AS date)")
    List<Object[]> findDailyTrafficBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT HOUR(o.createdAt), COUNT(DISTINCT o.buyer.id), COUNT(DISTINCT k.seller.id) " +
            "FROM Order o JOIN o.kitchen k " +
            "WHERE o.createdAt >= :start AND o.createdAt < :end " +
            "GROUP BY HOUR(o.createdAt) " +
            "ORDER BY HOUR(o.createdAt)")
    List<Object[]> findHourlyTrafficBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.id FROM Order o WHERE o.acknowledgedAt IS NULL AND o.remindedAt IS NULL " +
            "AND o.orderStatus IN :statuses AND COALESCE(o.orderTime, o.createdAt) < :before ORDER BY o.id")
    List<Long> findReminderCandidates(@Param("statuses") List<OrderStatus> statuses,
                                      @Param("before") LocalDateTime before, Pageable pageable);

    // ==================== DELIVERY COMPLETION (V1) ====================

    /**
     * Every order for one offering on one date, with items and products eagerly
     * fetched so the bulk delivery update can inspect them without N+1 queries.
     *
     * <p>DRAFT and CANCELLED are deliberately still returned: the CALLER decides
     * scope via {@code Order.isActiveForDelivery()}, which is the single shared
     * definition used by both the progress counters and the bulk write. Keeping
     * that decision in one place is what guarantees the number the seller is
     * asked to confirm is exactly the number of rows that get changed.</p>
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product " +
            "WHERE o.kitchen = :kitchen AND o.createdAt >= :start AND o.createdAt < :end " +
            "ORDER BY o.createdAt DESC")
    List<Order> findOfferingOrdersOnDateWithItems(@Param("kitchen") Kitchen kitchen,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);
}

