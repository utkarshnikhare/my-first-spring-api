package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.repository.EnquiryRepository;
import com.example.my_first_spring_api.repository.FavouriteRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the Admin dashboard order-status breakdown.
 *
 * <p>The buckets are new, Admin-only presentation data on /api/admin/dashboard.
 * They must be mutually exclusive and sum to totalOrders, so a single order can
 * never be counted twice, and cancelled orders must never leak into "awaiting"
 * or "fulfilled" (cancelled orders have already had inventory restored).
 */
class AdminDashboardOrderStatusTest {

    @Mock private OrderRepository orderRepository;
    @Mock private KitchenRepository kitchenRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnquiryRepository enquiryRepository;
    @Mock private FavouriteRepository favouriteRepository;

    @InjectMocks private AdminService adminService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void bucketsAreMutuallyExclusiveAndSumToTotalOrders() {
        givenOrders(OrderStatus.ORDERED, OrderStatus.ORDERED,
                OrderStatus.CONFIRMED, OrderStatus.READY,
                OrderStatus.DELIVERED, OrderStatus.COMPLETED,
                OrderStatus.CANCELLED);

        Map<String, Object> d = adminService.dashboard();

        assertThat(d.get("ordersAwaitingSellerConfirmation")).isEqualTo(2L);
        assertThat(d.get("ordersInFulfilment")).isEqualTo(2L);   // CONFIRMED + READY
        assertThat(d.get("ordersFulfilled")).isEqualTo(2L);       // DELIVERED + COMPLETED
        assertThat(d.get("ordersCancelled")).isEqualTo(1L);
        assertThat(d.get("ordersDraft")).isEqualTo(0L);
        assertThat(d.get("totalOrders")).isEqualTo(7L);
        assertThat(bucketSum(d)).as("every order lands in exactly one bucket")
                .isEqualTo(d.get("totalOrders"));
    }

    @Test
    void cancelledOrdersAreNotCountedAsOpenOrFulfilled() {
        givenOrders(OrderStatus.CANCELLED, OrderStatus.CANCELLED, OrderStatus.CANCELLED);

        Map<String, Object> d = adminService.dashboard();

        assertThat(d.get("ordersCancelled")).isEqualTo(3L);
        assertThat(d.get("ordersAwaitingSellerConfirmation")).isEqualTo(0L);
        assertThat(d.get("ordersInFulfilment")).isEqualTo(0L);
        assertThat(d.get("ordersFulfilled")).isEqualTo(0L);
    }

    @Test
    void rawPerStatusMapCoversEveryEnumConstant() {
        givenOrders(OrderStatus.ORDERED, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);

        // Keys are OrderStatus constants; Jackson renders them as their names
        // ("ORDERED", ...), which is what the console consumes.
        @SuppressWarnings("unchecked")
        Map<OrderStatus, Long> byStatus =
                (Map<OrderStatus, Long>) adminService.dashboard().get("ordersByStatus");

        assertThat(byStatus).containsEntry(OrderStatus.ORDERED, 1L)
                .containsEntry(OrderStatus.CONFIRMED, 1L);
        // Present even at zero so the console never reads an undefined value.
        assertThat(byStatus).containsKeys(OrderStatus.DRAFT, OrderStatus.ORDERED,
                OrderStatus.CONFIRMED, OrderStatus.READY, OrderStatus.DELIVERED,
                OrderStatus.COMPLETED, OrderStatus.CANCELLED);
    }

    @Test
    void breakdownIsZeroRatherThanAbsentWhenThereAreNoOrders() {
        givenOrders();

        Map<String, Object> d = adminService.dashboard();

        assertThat(d.get("totalOrders")).isEqualTo(0L);
        assertThat(d.get("ordersAwaitingSellerConfirmation")).isEqualTo(0L);
        assertThat(d.get("ordersCancelled")).isEqualTo(0L);
    }

    private long bucketSum(Map<String, Object> d) {
        long t = 0;
        for (String k : new String[]{"ordersAwaitingSellerConfirmation", "ordersInFulfilment",
                "ordersFulfilled", "ordersCancelled", "ordersDraft"}) {
            t += ((Number) d.get(k)).longValue();
        }
        return t;
    }

    private void givenOrders(OrderStatus... statuses) {
        List<Order> orders = new ArrayList<>();
        for (OrderStatus st : statuses) {
            Order o = new Order();
            o.setOrderStatus(st);
            o.setTotalAmount(new BigDecimal("100"));
            o.setCreatedAt(LocalDateTime.now());
            orders.add(o);
        }
        when(orderRepository.findAll()).thenReturn(orders);
    }
}
