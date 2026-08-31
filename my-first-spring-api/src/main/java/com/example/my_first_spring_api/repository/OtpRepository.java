package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByMobileNumberOrderByCreatedAtDesc(String mobileNumber);
}
