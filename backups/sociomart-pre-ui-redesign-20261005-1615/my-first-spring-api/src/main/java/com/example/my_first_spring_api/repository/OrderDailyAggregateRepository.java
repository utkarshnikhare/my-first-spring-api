package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.OrderDailyAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read/write access to the daily aggregate rollup that outlives detailed order rows. */
public interface OrderDailyAggregateRepository extends JpaRepository<OrderDailyAggregate, Long> {

    Optional<OrderDailyAggregate> findByDayAndSellerIdAndAreaIdAndSocietyIdAndCategory(
            LocalDate day, Long sellerId, Long areaId, Long societyId, String category);

    List<OrderDailyAggregate> findByDayBetweenOrderByDayAsc(LocalDate from, LocalDate to);

    List<OrderDailyAggregate> findBySellerIdOrderByDayDesc(Long sellerId);

    @Query("select coalesce(sum(a.orderCount), 0) from OrderDailyAggregate a where a.day between :from and :to")
    long sumOrderCount(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(a.recordedOrderValue), 0) from OrderDailyAggregate a where a.day between :from and :to")
    java.math.BigDecimal sumValue(@Param("from") LocalDate from, @Param("to") LocalDate to);
}