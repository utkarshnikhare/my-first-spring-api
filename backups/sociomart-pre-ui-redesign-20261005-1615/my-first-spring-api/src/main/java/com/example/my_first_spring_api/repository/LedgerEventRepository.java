package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.LedgerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEventRepository extends JpaRepository<LedgerEvent, Long> {
    List<LedgerEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
    List<LedgerEvent> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
}
