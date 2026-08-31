package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByKitchen(Kitchen kitchen);
    List<Product> findByAvailableTodayTrueOrderByCreatedAtDesc();
    List<Product> findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(Kitchen kitchen);
    List<Product> findByNameContainingIgnoreCase(String name);
    List<Product> findByKitchenId(Long kitchenId);
}
