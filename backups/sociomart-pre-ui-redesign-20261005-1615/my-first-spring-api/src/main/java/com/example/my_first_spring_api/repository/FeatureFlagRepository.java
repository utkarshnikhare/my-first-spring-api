package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    Optional<FeatureFlag> findByFeatureKey(String featureKey);
    boolean existsByFeatureKey(String featureKey);
}
