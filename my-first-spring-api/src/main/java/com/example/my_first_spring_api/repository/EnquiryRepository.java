package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Enquiry;
import com.example.my_first_spring_api.model.EnquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    List<Enquiry> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Enquiry> findByKitchenSellerIdOrderByCreatedAtDesc(Long sellerId);

    @Query("SELECT e FROM Enquiry e WHERE e.acknowledgedAt IS NULL AND e.status = :status AND e.createdAt < :before")
    List<Enquiry> findUnacknowledgedEnquiries(@Param("status") EnquiryStatus status, @Param("before") LocalDateTime before);
}
