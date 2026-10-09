package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.OccurrenceOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OccurrenceOverrideRepository extends JpaRepository<OccurrenceOverride, Long> {
    Optional<OccurrenceOverride> findByOccurrenceId(Long occurrenceId);
}
