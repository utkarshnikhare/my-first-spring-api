package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.SellerFeatureGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerFeatureGrantRepository extends JpaRepository<SellerFeatureGrant, Long> {
    Optional<SellerFeatureGrant> findBySellerIdAndFeatureKey(Long sellerId, String featureKey);
    List<SellerFeatureGrant> findBySellerId(Long sellerId);
    List<SellerFeatureGrant> findByFeatureKey(String featureKey);
}
