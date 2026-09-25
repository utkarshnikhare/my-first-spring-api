package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByProduct(Product product);
    List<OrderItem> findByProductId(Long productId);

    /**
     * Items belonging to a confirmed (non-draft) order. Used by the live-offering
     * edit rules (Requirement 5) so that an abandoned buyer draft never counts as
     * "the offering has orders" and never locks the seller's price/name/unit/date.
     */
    List<OrderItem> findByProductIdAndOrderOrderStatusNot(Long productId, OrderStatus orderStatus);
}
