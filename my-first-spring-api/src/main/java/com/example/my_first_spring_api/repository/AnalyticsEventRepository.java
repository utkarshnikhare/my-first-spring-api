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

    /**
     * Admin handover section 6: storefront views and offering views per
     * storefront, derived from events the application actually records. Returns
     * {@code [kitchenId, storefrontViews, offeringViews]}.
     *
     * <p>Counted over ALL time, not just today, because the Admin analytics
     * screen applies its own date filters to the order side and a storefront
     * that was never opened today would otherwise read as zero traffic forever.
     */
    @Query("select a.kitchenId, "
            + "sum(case when a.eventType = com.example.my_first_spring_api.service.AnalyticsService.EV_HOMEMADE_STOREFRONT_VIEW then 1 else 0 end), "
            + "sum(case when a.eventType = com.example.my_first_spring_api.service.AnalyticsService.EV_PRODUCT_VIEW then 1 else 0 end) "
            + "from AnalyticsEvent a "
            + "where a.kitchenId is not null "
            + "and a.eventType in (com.example.my_first_spring_api.service.AnalyticsService.EV_HOMEMADE_STOREFRONT_VIEW, "
            + "com.example.my_first_spring_api.service.AnalyticsService.EV_PRODUCT_VIEW) "
            + "group by a.kitchenId")
    List<Object[]> countStorefrontAndProductViewsByKitchen();
}
