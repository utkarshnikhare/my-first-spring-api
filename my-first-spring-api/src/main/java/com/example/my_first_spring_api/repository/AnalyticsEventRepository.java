package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.AnalyticsEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    long countByEventType(String eventType);

    long countByEventTypeAndCreatedAtAfter(String eventType, LocalDateTime after);

    long countByCreatedAtAfter(LocalDateTime after);

    long countByEventTypeAndKitchenIdAndCreatedAtAfter(String eventType, Long kitchenId, LocalDateTime after);

    @Query("select a.eventType, count(a) from AnalyticsEvent a group by a.eventType")
    List<Object[]> countGroupedByType();

    List<AnalyticsEvent> findByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select count(o) from Order o where o.orderStatus <> com.example.my_first_spring_api.model.OrderStatus.DRAFT")
    long countNonDraftOrders();
}
