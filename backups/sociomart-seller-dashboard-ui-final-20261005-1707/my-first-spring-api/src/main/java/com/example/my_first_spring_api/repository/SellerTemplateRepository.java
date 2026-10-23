package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.SellerTemplate;
import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerTemplateRepository extends JpaRepository<SellerTemplate, Long> {
    List<SellerTemplate> findBySellerOrderByCreatedAtDesc(User seller);
    long countBySeller(User seller);
}

