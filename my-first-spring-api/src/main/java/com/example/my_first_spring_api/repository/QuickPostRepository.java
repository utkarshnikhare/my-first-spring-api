package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.QuickPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuickPostRepository extends JpaRepository<QuickPost, Long> {
    List<QuickPost> findByKitchenAndPostedDateOrderByCreatedAtDesc(Kitchen kitchen, LocalDate postedDate);
    Optional<QuickPost> findByRequestId(String requestId);
}
