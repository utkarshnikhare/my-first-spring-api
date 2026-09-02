package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Enquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    List<Enquiry> findByUserIdOrderByCreatedAtDesc(Long userId);
}
