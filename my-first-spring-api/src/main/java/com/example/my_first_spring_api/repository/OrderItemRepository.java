package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
