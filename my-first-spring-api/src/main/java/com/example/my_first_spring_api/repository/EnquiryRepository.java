package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Enquiry;
import com.example.my_first_spring_api.model.EnquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Enquiry e WHERE e.id = :id")
    Optional<Enquiry> findByIdForUpdate(@Param("id") Long id);
    List<Enquiry> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Enquiry> findByKitchenSellerIdOrderByCreatedAtDesc(Long sellerId);

    @Query("SELECT e.id FROM Enquiry e WHERE e.acknowledgedAt IS NULL AND e.remindedAt IS NULL " +
            "AND e.status = :status AND e.createdAt < :before ORDER BY e.id")
    List<Long> findReminderCandidates(@Param("status") EnquiryStatus status,
                                      @Param("before") LocalDateTime before, Pageable pageable);
}
