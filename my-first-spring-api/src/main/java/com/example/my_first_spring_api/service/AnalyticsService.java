package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.AnalyticsEvent;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.repository.AnalyticsEventRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects lightweight traffic/usage events and summarises them for the
 * Admin / Super Admin analytics APIs. Dashboards are designed later —
 * this service is the single place where metrics are recorded and computed.
 */
@Service
public class AnalyticsService {

    public static final String EV_USER_REGISTERED = "USER_REGISTERED";
    public static final String EV_USER_LOGIN = "USER_LOGIN";
    public static final String EV_SELLER_REGISTERED = "SELLER_REGISTERED";
    public static final String EV_SELLER_APPROVED = "SELLER_APPROVED";
    public static final String EV_ORDER_PLACED = "ORDER_PLACED";
    public static final String EV_MENU_VIEW = "MENU_VIEW";
    public static final String EV_MARKETPLACE_VIEW = "MARKETPLACE_VIEW";

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Autowired
    public AnalyticsService(AnalyticsEventRepository analyticsEventRepository,
                            UserRepository userRepository,
                            OrderRepository orderRepository,
                            ProductRepository productRepository) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, Long userId, String userMobile, Long kitchenId, String detail) {
        try {
            analyticsEventRepository.save(new AnalyticsEvent(eventType, userId, userMobile, kitchenId, detail));
        } catch (RuntimeException ignored) {
            // Analytics must never break a user-facing action.
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        Map<String, Object> out = new LinkedHashMap<>();

        Map<String, Long> usersByRole = new LinkedHashMap<>();
        usersByRole.put("BUYERS", userRepository.countByRole(UserRole.BUYER));
        usersByRole.put("SELLERS", userRepository.countByRole(UserRole.SELLER));
        usersByRole.put("ADMINS", userRepository.countByRole(UserRole.ADMIN)
                + userRepository.countByRole(UserRole.SUPER_ADMIN));

        out.put("totalUsers", userRepository.count());
        out.put("usersByRole", usersByRole);
        out.put("sellersPending", userRepository.countByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.PENDING));
        out.put("sellersApproved", userRepository.countByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.APPROVED));
        out.put("sellersRejected", userRepository.countByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.REJECTED));
        out.put("sellersSuspended", userRepository.countByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.SUSPENDED));

        out.put("totalProducts", productRepository.count());
        out.put("totalOrders", orderRepository.count());
        out.put("ordersToday", orderRepository.countByCreatedAtAfter(startOfToday));

        Map<String, Long> eventsByType = new LinkedHashMap<>();
        for (Object[] row : analyticsEventRepository.countGroupedByType()) {
            eventsByType.put(String.valueOf(row[0]), (Long) row[1]);
        }
        out.put("eventsByType", eventsByType);
        out.put("menuViews", eventsByType.getOrDefault(EV_MENU_VIEW, 0L));
        out.put("marketplaceViews", eventsByType.getOrDefault(EV_MARKETPLACE_VIEW, 0L));
        out.put("signupsToday", analyticsEventRepository.countByEventTypeAndCreatedAtAfter(EV_USER_REGISTERED, startOfToday));
        out.put("eventsToday", analyticsEventRepository.countByCreatedAtAfter(startOfToday));

        List<Map<String, Object>> recent = analyticsEventRepository
                .findByOrderByCreatedAtDesc(PageRequest.of(0, 20)).stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("type", e.getEventType());
                    m.put("at", e.getCreatedAt());
                    m.put("detail", e.getDetail());
                    return m;
                })
                .toList();
        out.put("recentEvents", recent);
        return out;
    }
}
