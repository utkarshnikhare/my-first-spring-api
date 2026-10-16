package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.PasswordResetRequest;
import com.example.my_first_spring_api.model.PasswordResetRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Persistence for the manual (Admin-mediated) password reset requests. */
@Repository
public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    List<PasswordResetRequest> findByStatusOrderByCreatedAtDesc(PasswordResetRequestStatus status);

    List<PasswordResetRequest> findAllByOrderByCreatedAtDesc();

    /** Newest first, so login checks the most recent temporary password first. */
    List<PasswordResetRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
