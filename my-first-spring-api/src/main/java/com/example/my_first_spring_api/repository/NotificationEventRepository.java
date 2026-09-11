package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {
    List<NotificationEvent> findByUserIdAndDeliveredFalseOrderByCreatedAtDesc(Long userId);
}
