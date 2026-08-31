package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenRepository extends JpaRepository<Kitchen, Long> {
    Optional<Kitchen> findByName(String name);
    List<Kitchen> findBySeller(User seller);
    List<Kitchen> findByAvailableTodayTrue();
}
