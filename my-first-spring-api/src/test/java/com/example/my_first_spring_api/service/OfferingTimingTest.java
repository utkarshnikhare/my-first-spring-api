package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferingTimingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    @Test
    void todayOfferingResolvesToTodayAndAcceptsImmediateWindow() {
        OfferingTiming.validateNewOffering(TODAY, false, null, "23:58",
                "2026-09-24T23:59", TODAY, LocalTime.of(12, 0));
    }

    @Test
    void tomorrowOfferingResolvesToTomorrow() {
        OfferingTiming.validateNewOffering(TODAY.plusDays(1), true, null, "23:58",
                "2026-09-25T23:59", TODAY, LocalTime.of(12, 0));
    }

    @Test
    void chosenFutureDateIsAccepted() {
        OfferingTiming.validateNewOffering(TODAY.plusDays(3), true, "18:00", "23:58",
                "2026-09-27T23:59", TODAY, LocalTime.of(12, 0));
    }

    @Test
    void missingCloseAndReadyAreRejected() {
        assertThatThrownBy(() -> OfferingTiming.validateNewOffering(TODAY, false,
                null, null, "2026-09-24T23:59", TODAY, LocalTime.NOON))
                .hasMessageContaining("Orders Close is required");
        assertThatThrownBy(() -> OfferingTiming.validateNewOffering(TODAY, false,
                null, "23:58", null, TODAY, LocalTime.NOON))
                .hasMessageContaining("Delivery / Ready By is required");
    }

    @Test
    void openAfterCloseIsRejected() {
        assertThatThrownBy(() -> OfferingTiming.validateNewOffering(TODAY, false,
                "18:00", "17:00", "2026-09-24T23:59", TODAY, LocalTime.NOON))
                .hasMessageContaining("Orders Open must be before Orders Close");
    }

    @Test
    void closeAfterReadyIsRejected() {
        assertThatThrownBy(() -> OfferingTiming.validateNewOffering(TODAY, false,
                null, "23:59", "2026-09-24T18:00", TODAY, LocalTime.NOON))
                .hasMessageContaining("Orders Close must be before Delivery");
    }

    @Test
    void deliveryBeforeOfferingIsRejected() {
        assertThatThrownBy(() -> OfferingTiming.validateNewOffering(TODAY.plusDays(1), true,
                null, "23:58", "2026-09-24T18:00", TODAY, LocalTime.NOON))
                .hasMessageContaining("cannot be before");
    }

    @Test
    void sameDayPreorderIsRejected() {
        assertThatThrownBy(() -> OfferingTiming.validateNewOffering(TODAY, true,
                null, "23:58", "2026-09-24T23:59", TODAY, LocalTime.NOON))
                .hasMessageContaining("future offering date");
    }

    @Test
    void futureOrderingDateWithConfiguredOpenIsNotYetOrderable() {
        Product product = product(TODAY.plusDays(2), true, "18:00", "23:58", 10);
        assertThatThrownBy(() -> OfferingTiming.enforceOrderWindow(product,
                TODAY.plusDays(1), "today", TODAY, LocalTime.of(12, 0)))
                .hasMessageContaining("have not opened yet");
    }

    @Test
    void futureOrderingDateWithBlankOpenIsOrderableUntilCloseDate() {
        Product product = product(TODAY.plusDays(2), true, null, "23:58", 10);
        OfferingTiming.enforceOrderWindow(product, TODAY.plusDays(1), "today",
                TODAY, LocalTime.of(12, 0));
    }

    @Test
    void soldOutAndPausedAreDistinctAndBlockOrders() {
        Product soldOut = product(TODAY, false, null, "23:58", 0);
        Product paused = product(TODAY, false, null, "23:58", 10);
        paused.setOrdersPaused(true);
        assertThat(soldOut.isSoldOut()).isTrue();
        assertThat(paused.isSoldOut()).isFalse();
        assertThat(OfferingTiming.isWindowOpenNow(soldOut, TODAY, LocalTime.NOON)).isFalse();
        assertThat(OfferingTiming.isWindowOpenNow(paused, TODAY, LocalTime.NOON)).isFalse();
    }

    @Test
    void closeIsEnforcedOnItsOrderingDate() {
        Product product = product(TODAY, false, null, "12:00", 10);
        assertThatThrownBy(() -> OfferingTiming.enforceOrderWindow(product,
                TODAY, "today", TODAY, LocalTime.of(13, 0)))
                .hasMessageContaining("orders today are closed");
    }

    @Test
    void savedReadyTimeIsRebasedToNewOfferingDate() {
        assertThat(OfferingTiming.rebaseReadyByTime("2026-09-24T13:00",
                LocalDate.of(2026, 9, 27))).isEqualTo("2026-09-27T13:00");
    }

    @Test
    void lifecycleUsesServerTimeAndKeepsCloseStateDistinct() {
        Product live = product(TODAY, false, null, "12:00", 10);
        assertThat(OfferingTiming.lifecycleState(live, TODAY, LocalTime.of(11, 59))).isEqualTo("LIVE");
        assertThat(OfferingTiming.lifecycleState(live, TODAY, LocalTime.of(12, 0))).isEqualTo("LIVE");
        assertThat(OfferingTiming.lifecycleState(live, TODAY, LocalTime.of(12, 1))).isEqualTo("ORDERS_CLOSED");
    }

    @Test
    void pausedAndSoldOutTakePrecedenceOverOrdersClosed() {
        Product paused = product(TODAY, false, null, "12:00", 10);
        paused.setOrdersPaused(true);
        Product soldOut = product(TODAY, false, null, "12:00", 0);
        assertThat(OfferingTiming.lifecycleState(paused, TODAY, LocalTime.of(13, 0))).isEqualTo("PAUSED");
        assertThat(OfferingTiming.lifecycleState(soldOut, TODAY, LocalTime.of(13, 0))).isEqualTo("SOLD_OUT");
    }

    @Test
    void yesterdayOfferingIsHistoryWhileCurrentAndFutureRemainOutOfHistory() {
        assertThat(OfferingTiming.lifecycleState(product(TODAY.minusDays(1), false, null, "12:00", 10),
                TODAY, LocalTime.NOON)).isEqualTo("HISTORY");
        assertThat(OfferingTiming.lifecycleState(product(TODAY, false, null, "23:58", 10),
                TODAY, LocalTime.NOON)).isEqualTo("LIVE");
        assertThat(OfferingTiming.lifecycleState(product(TODAY.plusDays(1), true, null, "23:58", 10),
                TODAY, LocalTime.NOON)).isEqualTo("PRE_ORDER");
    }


    private Product product(LocalDate date, boolean preorder, String open, String close, int remaining) {
        User seller = new User("Seller", "9100000001", "A-1", UserRole.SELLER);
        seller.setId(1L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        Product product = new Product(kitchen, "Poha", "d", java.math.BigDecimal.ONE, null);
        product.setAvailableDate(date);
        product.setAvailableToday(!preorder && TODAY.equals(date));
        product.setIsPreorder(preorder);
        product.setOrderWindowStart(open);
        product.setOrderWindowEnd(close);
        product.setCutoffTime(close);
        product.setMaxQuantity(remaining == 0 ? 1 : remaining);
        product.setRemainingQuantity(remaining);
        return product;
    }
}
