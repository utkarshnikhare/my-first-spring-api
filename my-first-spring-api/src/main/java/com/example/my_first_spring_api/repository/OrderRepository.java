package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerOrderByCreatedAtDesc(User buyer);
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
}

