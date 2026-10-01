package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AreaRepository extends JpaRepository<Area, Long> {

    Optional<Area> findByNameIgnoreCase(String name);

    /** Active areas only, for every NEW selection (buyer dropdown, seller UI). */
    List<Area> findByActiveTrueOrderByNameAsc();
}
