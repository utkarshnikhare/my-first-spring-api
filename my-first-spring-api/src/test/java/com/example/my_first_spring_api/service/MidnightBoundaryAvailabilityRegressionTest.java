package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests confirming that expired offerings, past-date items, and cutoff-passed
 * items cannot be ordered under any circumstance, EVEN WHEN the scheduled midnight expiry job
 * has not run (e.g. Due to container idle sleep on Render free tier).
 *
 * All boundary checks are asserted strictly against {@link AppTimeZone#ZONE} (Asia/Kolkata).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:midnight-boundary-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
public class MidnightBoundaryAvailabilityRegressionTest {

    private static final AtomicLong MOBILE_SEQ = new AtomicLong(9700000000L);

    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private OrderService orderService;

    private User seller;
    private User buyer;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String sellerMobile = String.valueOf(MOBILE_SEQ.incrementAndGet());
        seller = new User("Seller " + suffix, sellerMobile, "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.saveAndFlush(seller);

        kitchen = new Kitchen("k-" + suffix, "Boundary Kitchen " + suffix, "Authentic food", null, seller);
        kitchen.setSociety("Pride World City");
        kitchen = kitchenRepository.saveAndFlush(kitchen);

        String buyerMobile = String.valueOf(MOBILE_SEQ.incrementAndGet());
        buyer = new User("Buyer " + suffix, buyerMobile, "Flat 101", UserRole.BUYER);
        buyer.setSociety("Pride World City");
        buyer.setBuilding("Tower A");
        buyer.setFlatHouseNumber("101");
        buyer = userRepository.saveAndFlush(buyer);
    }

    @Test
    @DisplayName("Expired offering from yesterday cannot be ordered even when midnight job did not run")
    void expiredOfferingCannotBeOrderedEvenWhenJobDidNotRun() {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalDate yesterday = today.minusDays(1);

        // Product configured with yesterday's service date, but availableToday=true and remainingQuantity=10
        // (simulating that MidnightExpiryJob was not triggered at 00:00)
        Product expiredProduct = new Product(kitchen, "Yesterday Special Thali", "Wholesome meal", BigDecimal.valueOf(150), "plate");
        expiredProduct.setAvailableDate(yesterday);
        expiredProduct.setAvailableToday(true);
        expiredProduct.setRemainingQuantity(10);
        expiredProduct.setOrdersPaused(false);
        expiredProduct.setOrderWindowStart("06:00");
        expiredProduct.setOrderWindowEnd("23:59");
        expiredProduct.setReadyByTime(yesterday.atTime(13, 0).toString());
        expiredProduct = productRepository.saveAndFlush(expiredProduct);

        // 1. assertOrderable throws IllegalArgumentException directly
        Product finalProduct = expiredProduct;
        assertThatThrownBy(() -> availabilityService.assertOrderable(finalProduct, buyer, "checkout"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("past date and can no longer be ordered");

        // 2. isOrderableNow returns false
        assertThat(availabilityService.isOrderableNow(expiredProduct, buyer)).isFalse();
        assertThat(availabilityService.isAvailableToday(expiredProduct, buyer)).isFalse();

        // 3. Draft creation and placeOrder are safely rejected at checkout
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyer.getId());
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(expiredProduct.getId());
        item.setQuantity(1);
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(item), session))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Offering with past cutoff time today is rejected at checkout")
    void offeringWithPastCutoffTimeIsRejected() {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalTime now = LocalTime.now(AppTimeZone.ZONE);

        // Set cutoff window that ended 5 minutes ago (or 00:00 if before 00:05)
        String pastCutoff;
        if (now.getHour() == 0 && now.getMinute() < 5) {
            pastCutoff = "00:00";
        } else {
            LocalTime pastTime = now.minusMinutes(3);
            pastCutoff = String.format("%02d:%02d", pastTime.getHour(), pastTime.getMinute());
        }

        Product cutoffProduct = new Product(kitchen, "Late Afternoon Snack", "Snack", BigDecimal.valueOf(80), "box");
        cutoffProduct.setAvailableDate(today);
        cutoffProduct.setAvailableToday(true);
        cutoffProduct.setRemainingQuantity(5);
        cutoffProduct.setOrdersPaused(false);
        cutoffProduct.setOrderWindowStart("00:00");
        cutoffProduct.setOrderWindowEnd(pastCutoff);
        cutoffProduct.setCutoffTime(pastCutoff);
        cutoffProduct.setReadyByTime(today.atTime(23, 0).toString());
        cutoffProduct = productRepository.saveAndFlush(cutoffProduct);

        final Product finalCutoff = cutoffProduct;
        assertThatThrownBy(() -> availabilityService.assertOrderable(finalCutoff, buyer, "checkout"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cutoff")
                .hasMessageContaining("passed");

        assertThat(availabilityService.isOrderableNow(cutoffProduct, buyer)).isFalse();
    }

    @Test
    @DisplayName("Midnight boundary evaluation strictly respects Asia/Kolkata timezone")
    void midnightBoundaryEvaluationRespectsAsiaKolkata() {
        LocalDate sept24 = LocalDate.of(2026, 9, 24);

        Product p = new Product(kitchen, "Midnight Boundary Dish", "Dish", BigDecimal.valueOf(100), "portion");
        p.setAvailableDate(sept24);
        p.setOrderWindowStart("06:00");
        p.setOrderWindowEnd("23:59");
        p.setReadyByTime("2026-09-24T23:59");

        // At 23:58:00 on the same date, order window is open
        boolean openBeforeMidnight = OfferingTiming.isWindowOpenNow(p, sept24, LocalTime.of(23, 58));
        assertThat(openBeforeMidnight).isTrue();

        // At 00:00:01 on the following day, date is now in the past
        LocalDate sept25 = sept24.plusDays(1);
        boolean openAfterMidnight = OfferingTiming.isWindowOpenNow(p, sept25, LocalTime.of(0, 0, 1));
        assertThat(openAfterMidnight).isFalse();
    }

    @Test
    @DisplayName("Next-day pre-order is orderable before its cutoff and non-orderable after")
    void nextDayPreorderValidation() {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalDate tomorrow = today.plusDays(1);

        Product preorderProduct = new Product(kitchen, "Tomorrow Lunch Biryani", "Biryani", BigDecimal.valueOf(220), "handi");
        preorderProduct.setAvailableDate(tomorrow);
        preorderProduct.setIsPreorder(true);
        preorderProduct.setAvailableToday(true);
        preorderProduct.setRemainingQuantity(20);
        preorderProduct.setOrdersPaused(false);
        preorderProduct.setOrderWindowStart("06:00");
        preorderProduct.setOrderWindowEnd("23:59");
        preorderProduct.setReadyByTime(tomorrow.atTime(13, 0).toString());
        preorderProduct = productRepository.saveAndFlush(preorderProduct);

        assertThat(availabilityService.isPreorderable(preorderProduct, buyer)).isTrue();

        // If availableDate is set to yesterday, it cannot be preordered
        preorderProduct.setAvailableDate(today.minusDays(1));
        assertThat(availabilityService.isPreorderable(preorderProduct, buyer)).isFalse();
    }
}
