package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.KitchenSlugAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KitchenSlugAliasRepository extends JpaRepository<KitchenSlugAlias, Long> {
    Optional<KitchenSlugAlias> findByOldSlug(String oldSlug);
    boolean existsByOldSlug(String oldSlug);
}
