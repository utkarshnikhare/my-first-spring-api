package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Deletes the line items of the given orders.
     *
     * <p>Needed by the retention purge (handover 11): ORDER_ITEMS carries a hard,
     * non-nullable FK to ORDERS, so the children must go first or the delete
     * fails on referential integrity.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OrderItem i where i.order.id in :ids")
    void deleteByOrderIds(@Param("ids") Collection<Long> ids);
    List<OrderItem> findByProduct(Product product);
    List<OrderItem> findByProductId(Long productId);

    /**
     * Items belonging to a confirmed (non-draft) order. Used by the live-offering
     * edit rules (Requirement 5) so that an abandoned buyer draft never counts as
     * "the offering has orders" and never locks the seller's price/name/unit/date.
     */
    List<OrderItem> findByProductIdAndOrderOrderStatusNot(Long productId, OrderStatus orderStatus);
}
