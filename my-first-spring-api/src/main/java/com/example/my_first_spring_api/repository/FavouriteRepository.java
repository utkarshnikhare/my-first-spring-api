package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Favourite;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavouriteRepository extends JpaRepository<Favourite, Long> {
    List<Favourite> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Favourite> findByUserIdAndKitchenId(Long userId, Long kitchenId);
    Optional<Favourite> findByUserIdAndProductId(Long userId, Long productId);
    long countByUser(User user);
    boolean existsByKitchen(Kitchen kitchen);
    boolean existsByProduct(Product product);
    long countByKitchen(Kitchen kitchen);
}
