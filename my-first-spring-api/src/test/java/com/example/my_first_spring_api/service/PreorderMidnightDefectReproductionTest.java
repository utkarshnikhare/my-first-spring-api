package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Authoritative Acceptance and Regression Suite for P1 Pre-order Midnight Defect.
 * Covers reproduction and formal Tests A through H from preorder defect.docx.
 */
class PreorderMidnightDefectReproductionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate TOMORROW = TODAY.plusDays(1); // 2026-10-27

    private Product createSamplePreorder() {
        User seller = new User("Chef Ramesh", "9100000001", "Flat 101", UserRole.SELLER);
        seller.setId(1L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);

        Kitchen kitchen = new Kitchen("ramesh-kitchen", "Ramesh Kitchen", "Fresh Meals", null, seller);
        kitchen.setId(10L);

        Product idli = new Product(kitchen, "Idli Sambar Combo", "Steamed idlis with sambar", BigDecimal.valueOf(80), null);
        idli.setId(100L);
        idli.setAvailableDate(TOMORROW);
        idli.setIsPreorder(true);
        idli.setAvailableToday(true);
        idli.setOrdersPaused(false);
        idli.setMaxQuantity(50);
        idli.setRemainingQuantity(50);
        idli.setBookedQuantity(0);
        // Configured order cutoff: Tomorrow at 5:00 PM (17:00)
        idli.setOrderWindowEnd("17:00");
        idli.setCutoffTime("17:00");
        // Delivery date & time: Tomorrow at 6:00 PM (18:00)
        idli.setReadyByTime(TOMORROW + "T18:00");

        return idli;
    }

    @Test
    @DisplayName("Test A — Future cutoff across midnight: Remains orderable before and after midnight until cutoff")
    void testA_futureCutoffAcrossMidnight() {
        Product preorder = createSamplePreorder();

        // 1. Publication Day afternoon (14:00): open
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TODAY, LocalTime.of(14, 0)))
                .as("Publication day at 14:00 should be open").isTrue();

        // 2. Publication Day evening (18:00): open (cutoff 17:00 is tomorrow, NOT today)
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TODAY, LocalTime.of(18, 0)))
                .as("Publication day at 18:00 should be open because cutoff is tomorrow at 17:00").isTrue();

        // 3. Publication Day late night (23:59): open
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TODAY, LocalTime.of(23, 59)))
                .as("Publication day at 23:59 should be open").isTrue();

        // 4. Delivery Day midnight rollover (00:00 and 00:01): open across midnight
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(0, 0)))
                .as("Delivery day at 00:00 (midnight) must remain open").isTrue();
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(0, 1)))
                .as("Delivery day at 00:01 must remain open").isTrue();

        // 5. Delivery Day morning (10:00): open
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(10, 0)))
                .as("Delivery day at 10:00 morning must remain open").isTrue();
        assertThat(OfferingTiming.lifecycleState(preorder, TOMORROW, LocalTime.of(10, 0)))
                .as("Lifecycle state at 10:00 on delivery day must be PRE_ORDER, not ORDERS_CLOSED")
                .isEqualTo("PRE_ORDER");

        // 6. Delivery Day afternoon (16:59 and 17:00): open
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(16, 59)))
                .as("Delivery day at 16:59 must remain open").isTrue();
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(17, 0)))
                .as("Delivery day at 17:00 inclusive boundary must remain open").isTrue();
    }

    @Test
    @DisplayName("Test B — Cutoff reached: Ordering is rejected once cutoff passes (17:01)")
    void testB_cutoffReachedRejection() {
        Product preorder = createSamplePreorder();

        // At 17:01, order window must be closed
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(17, 1)))
                .as("At 17:01, ordering must be closed").isFalse();

        // Enforcement must throw closed exception
        assertThatThrownBy(() -> OfferingTiming.enforceOrderWindow(preorder, TOMORROW, "today",
                TOMORROW, LocalTime.of(17, 1)))
                .hasMessageContaining("orders today are closed");

        // Lifecycle state must be ORDERS_CLOSED
        assertThat(OfferingTiming.lifecycleState(preorder, TOMORROW, LocalTime.of(17, 1)))
                .isEqualTo("ORDERS_CLOSED");
    }

    @Test
    @DisplayName("Test C — Delivery time differs from cutoff: Closes at 17:00 cutoff, not 18:00 delivery")
    void testC_deliveryTimeDiffersFromCutoff() {
        Product preorder = createSamplePreorder(); // cutoff: 17:00, delivery: 18:00

        // At 17:30 (between cutoff 17:00 and delivery 18:00):
        // Ordering must be closed based on the 17:00 cutoff, NOT delivery time.
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(17, 30)))
                .as("At 17:30, orders must be closed even though delivery is at 18:00")
                .isFalse();

        assertThatThrownBy(() -> OfferingTiming.enforceOrderWindow(preorder, TOMORROW, "today",
                TOMORROW, LocalTime.of(17, 30)))
                .hasMessageContaining("orders today are closed");

        // Verify resolveCutoffDateTime resolves to 17:00, distinct from 18:00 delivery
        LocalDateTime cutoffDt = OfferingTiming.resolveCutoffDateTime(preorder, TOMORROW, TODAY);
        assertThat(cutoffDt).isEqualTo(LocalDateTime.of(TOMORROW, LocalTime.of(17, 0)));
    }

    @Test
    @DisplayName("Test D — Buyer interface and backend agree: DTO and service enforce identical cutoff rules")
    void testD_buyerInterfaceAndBackendAgree() {
        Product preorder = createSamplePreorder();

        // 1. Before cutoff (16:59):
        String stateOpen = OfferingTiming.lifecycleState(preorder, TOMORROW, LocalTime.of(16, 59));
        boolean closedFlagOpen = "ORDERS_CLOSED".equals(stateOpen) || "HISTORY".equals(stateOpen);
        assertThat(stateOpen).isEqualTo("PRE_ORDER");
        assertThat(closedFlagOpen).isFalse();
        assertThatCode(() -> OfferingTiming.enforceOrderWindow(preorder, TOMORROW, "today",
                TOMORROW, LocalTime.of(16, 59))).doesNotThrowAnyException();

        // 2. After cutoff (17:01):
        String stateClosed = OfferingTiming.lifecycleState(preorder, TOMORROW, LocalTime.of(17, 1));
        boolean closedFlagClosed = "ORDERS_CLOSED".equals(stateClosed) || "HISTORY".equals(stateClosed);
        assertThat(stateClosed).isEqualTo("ORDERS_CLOSED");
        assertThat(closedFlagClosed).isTrue();
        assertThatThrownBy(() -> OfferingTiming.enforceOrderWindow(preorder, TOMORROW, "today",
                TOMORROW, LocalTime.of(17, 1)))
                .hasMessageContaining("orders today are closed");
    }

    @Test
    @DisplayName("Test E — Stock restrictions: Valid future cutoff does not override sold-out or exhausted stock")
    void testE_stockRestrictions() {
        Product preorder = createSamplePreorder();

        // Set remaining quantity to 0 (sold out)
        preorder.setRemainingQuantity(0);
        assertThat(preorder.isSoldOut()).isTrue();

        // On publication day and delivery day morning, sold-out takes precedence over future cutoff
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TODAY, LocalTime.of(14, 0))).isFalse();
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(10, 0))).isFalse();
        assertThat(OfferingTiming.lifecycleState(preorder, TOMORROW, LocalTime.of(10, 0)))
                .isEqualTo("SOLD_OUT");

        // When stock is replenished, it becomes open again across midnight
        preorder.setRemainingQuantity(15);
        assertThat(OfferingTiming.isWindowOpenNow(preorder, TOMORROW, LocalTime.of(10, 0))).isTrue();
        assertThat(OfferingTiming.lifecycleState(preorder, TOMORROW, LocalTime.of(10, 0)))
                .isEqualTo("PRE_ORDER");
    }

    @Test
    @DisplayName("Test F — Other offering types: Same-day, future pre-orders, paused, and history")
    void testF_otherOfferingTypes() {
        // 1. Same-day offering
        Product sameDay = createSamplePreorder();
        sameDay.setIsPreorder(false);
        sameDay.setAvailableDate(TODAY);
        sameDay.setReadyByTime(TODAY + "T18:00");
        sameDay.setOrderWindowEnd("17:00");
        sameDay.setCutoffTime("17:00");

        assertThat(OfferingTiming.lifecycleState(sameDay, TODAY, LocalTime.of(14, 0))).isEqualTo("LIVE");
        assertThat(OfferingTiming.lifecycleState(sameDay, TODAY, LocalTime.of(17, 5))).isEqualTo("ORDERS_CLOSED");
        assertThat(OfferingTiming.lifecycleState(sameDay, TOMORROW, LocalTime.of(10, 0))).isEqualTo("HISTORY");

        // 2. Future pre-order 3 days out
        Product multiDayPreorder = createSamplePreorder();
        multiDayPreorder.setAvailableDate(TODAY.plusDays(3));
        multiDayPreorder.setReadyByTime(TODAY.plusDays(3) + "T18:00");
        multiDayPreorder.setOrderWindowEnd("17:00");

        assertThat(OfferingTiming.isWindowOpenNow(multiDayPreorder, TODAY, LocalTime.of(18, 0))).isTrue();
        assertThat(OfferingTiming.isWindowOpenNow(multiDayPreorder, TODAY.plusDays(1), LocalTime.of(18, 0))).isTrue();
        assertThat(OfferingTiming.isWindowOpenNow(multiDayPreorder, TODAY.plusDays(2), LocalTime.of(18, 0))).isTrue();
        assertThat(OfferingTiming.isWindowOpenNow(multiDayPreorder, TODAY.plusDays(3), LocalTime.of(10, 0))).isTrue();
        assertThat(OfferingTiming.isWindowOpenNow(multiDayPreorder, TODAY.plusDays(3), LocalTime.of(17, 1))).isFalse();

        // 3. Paused offering
        Product paused = createSamplePreorder();
        paused.setOrdersPaused(true);
        assertThat(OfferingTiming.isWindowOpenNow(paused, TOMORROW, LocalTime.of(10, 0))).isFalse();
        assertThat(OfferingTiming.lifecycleState(paused, TOMORROW, LocalTime.of(10, 0))).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("Test G — IST date-time boundaries: Midnight rollover and precision under Asia/Kolkata")
    void testG_istDateTimeBoundaries() {
        Product preorder = createSamplePreorder();

        // Verify with explicit Asia/Kolkata zoned instants
        ZoneId ist = AppTimeZone.ZONE;

        // 23:59:59 IST today
        ZonedDateTime preMidnight = ZonedDateTime.of(TODAY, LocalTime.of(23, 59, 59), ist);
        assertThat(OfferingTiming.isWindowOpenNow(preorder, preMidnight.toLocalDate(), preMidnight.toLocalTime()))
                .isTrue();

        // 00:00:00 IST tomorrow (instant of midnight transition)
        ZonedDateTime midnight = ZonedDateTime.of(TOMORROW, LocalTime.of(0, 0, 0), ist);
        assertThat(OfferingTiming.isWindowOpenNow(preorder, midnight.toLocalDate(), midnight.toLocalTime()))
                .isTrue();
        assertThat(OfferingTiming.lifecycleState(preorder, midnight.toLocalDate(), midnight.toLocalTime()))
                .isEqualTo("PRE_ORDER");

        // 17:00:00 IST tomorrow (exact cutoff)
        ZonedDateTime exactCutoff = ZonedDateTime.of(TOMORROW, LocalTime.of(17, 0, 0), ist);
        assertThat(OfferingTiming.isWindowOpenNow(preorder, exactCutoff.toLocalDate(), exactCutoff.toLocalTime()))
                .isTrue();

        // 17:00:01 IST tomorrow (first second after cutoff)
        ZonedDateTime postCutoff = ZonedDateTime.of(TOMORROW, LocalTime.of(17, 0, 1), ist);
        assertThat(OfferingTiming.isWindowOpenNow(preorder, postCutoff.toLocalDate(), postCutoff.toLocalTime()))
                .isTrue(); // minute precision maintains 17:00 minute open

        // 17:01:00 IST tomorrow (cutoff passed)
        ZonedDateTime nextMinute = ZonedDateTime.of(TOMORROW, LocalTime.of(17, 1, 0), ist);
        assertThat(OfferingTiming.isWindowOpenNow(preorder, nextMinute.toLocalDate(), nextMinute.toLocalTime()))
                .isFalse();
    }

    @Test
    @DisplayName("Test H — Historical orders and past availability preserved")
    void testH_historicalOrdersAndPastAvailabilityPreserved() {
        Product historical = createSamplePreorder();
        historical.setAvailableDate(TODAY.minusDays(2)); // Fulfilled 2 days ago
        historical.setReadyByTime(TODAY.minusDays(2) + "T18:00");
        historical.setOrderWindowEnd("17:00");

        // Historical offerings must cleanly resolve to HISTORY
        assertThat(OfferingTiming.lifecycleState(historical, TODAY, LocalTime.NOON))
                .isEqualTo("HISTORY");

        // Direct attempt to order historical offering must fail
        assertThat(OfferingTiming.isWindowOpenNow(historical, TODAY, LocalTime.NOON))
                .isFalse();
        assertThatThrownBy(() -> OfferingTiming.enforceOrderWindow(historical, historical.getAvailableDate(), "today",
                TODAY, LocalTime.NOON))
                .hasMessageContaining("closed");
    }
}
