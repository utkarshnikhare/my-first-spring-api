package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenRepository extends JpaRepository<Kitchen, Long> {
    Optional<Kitchen> findByName(String name);
    Optional<Kitchen> findBySlug(String slug);
    List<Kitchen> findBySeller(User seller);
    List<Kitchen> findByAvailableTodayTrue();

    /** Rows still lacking a public-URL slug (null or blank) - the only ones the start-up backfill touches. */
    @Query("select k from Kitchen k where k.slug is null or trim(k.slug) = ''")
    List<Kitchen> findWithMissingSlug();
}
