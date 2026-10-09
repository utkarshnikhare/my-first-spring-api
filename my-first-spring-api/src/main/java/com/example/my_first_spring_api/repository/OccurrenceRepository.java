package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Occurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {
    Optional<Occurrence> findByScheduleIdAndOccurrenceDate(Long scheduleId, LocalDate date);
    List<Occurrence> findByScheduleIdOrderByOccurrenceDateAsc(Long scheduleId);
    List<Occurrence> findByScheduleId(Long scheduleId);
    boolean existsByScheduleIdAndOccurrenceDate(Long scheduleId, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Occurrence o where o.id = :id")
    Optional<Occurrence> findByIdForUpdate(@Param("id") Long id);
}
