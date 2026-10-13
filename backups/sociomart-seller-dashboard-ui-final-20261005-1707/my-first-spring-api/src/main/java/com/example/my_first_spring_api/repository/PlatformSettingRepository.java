package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {
    Optional<PlatformSetting> findBySettingKey(String settingKey);
}
