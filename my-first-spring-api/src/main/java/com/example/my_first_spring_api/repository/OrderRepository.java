package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

