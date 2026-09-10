package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByProduct(Product product);
    List<OrderItem> findByProductId(Long productId);
}
