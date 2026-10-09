package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.RecurringSchedule;
import com.example.my_first_spring_api.model.RecurringScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringScheduleRepository extends JpaRepository<RecurringSchedule, Long> {
    Optional<RecurringSchedule> findByProductId(Long productId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecurringSchedule> findLockedById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecurringSchedule> findLockedByProductId(Long productId);
    List<RecurringSchedule> findByProduct_Kitchen_Seller_Id(Long sellerId);
    List<RecurringSchedule> findByStatus(RecurringScheduleStatus status);
}
