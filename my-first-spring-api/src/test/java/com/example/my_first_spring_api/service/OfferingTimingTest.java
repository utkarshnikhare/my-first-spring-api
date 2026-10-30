package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.dto.ProductDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    void openingTodayWithNoTimeUsesAuthoritativePublicationTime() {
        assertThat(OfferingTiming.validateOrdersOpenFrom(TODAY, null, TODAY.plusDays(1),
                "17:00", "2026-09-25T18:00", TODAY, LocalTime.of(12, 34, 56)))
                .isEqualTo(LocalDateTime.of(TODAY, LocalTime.of(12, 34, 56)));
    }

    @Test
    void futureOpeningRequiresTimeAndMustPrecedeCutoffAndDelivery() {
        assertThat(OfferingTiming.validateOrdersOpenFrom(TODAY.plusDays(1), "09:00",
                TODAY.plusDays(1), "17:00", "2026-09-25T18:00", TODAY, LocalTime.NOON))
                .isEqualTo(LocalDateTime.of(TODAY.plusDays(1), LocalTime.of(9, 0)));
        assertThatThrownBy(() -> OfferingTiming.validateOrdersOpenFrom(TODAY.plusDays(1), null,
                TODAY.plusDays(1), "17:00", "2026-09-25T18:00", TODAY, LocalTime.NOON))
                .hasMessageContaining("opening time");
        assertThatThrownBy(() -> OfferingTiming.validateOrdersOpenFrom(TODAY.plusDays(1), "17:00",
                TODAY.plusDays(1), "17:00", "2026-09-25T18:00", TODAY, LocalTime.NOON))
                .hasMessageContaining("before Orders Close");
        assertThatThrownBy(() -> OfferingTiming.validateOrdersOpenFrom(TODAY.plusDays(1), "18:00",
                TODAY.plusDays(1), "23:00", "2026-09-25T18:00", TODAY, LocalTime.NOON))
                .hasMessageContaining("before Delivery");
    }

    @Test
    void pastOpeningDateAndPastTodayTimeAreRejected() {
        assertThatThrownBy(() -> OfferingTiming.validateOrdersOpenFrom(TODAY.minusDays(1), "09:00",
                TODAY, "17:00", "2026-09-24T18:00", TODAY, LocalTime.NOON))
                .hasMessageContaining("cannot be in the past");
        assertThatThrownBy(() -> OfferingTiming.validateOrdersOpenFrom(TODAY, "11:59",
                TODAY, "17:00", "2026-09-24T18:00", TODAY, LocalTime.NOON))
                .hasMessageContaining("has passed");
    }

    @Test
    void missingCloseIsAutoHealedAndMissingReadyIsRejected() {
        String healed = OfferingTiming.validateNewOffering(TODAY, false,
                null, null, "2026-09-24T23:59", TODAY, LocalTime.NOON);
        assertThat(healed).isEqualTo("22:59");

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
    void closeAfterReadyIsAutoHealed() {
        String healed = OfferingTiming.validateNewOffering(TODAY, false,
                null, "23:59", "2026-09-24T18:00", TODAY, LocalTime.NOON);
        assertThat(healed).isEqualTo("17:00");
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
    void futureOpeningTimestampBlocksOrderingAndReportsScheduledState() {
        Product product = product(TODAY.plusDays(1), true, null, "23:58", 10);
        product.setOrdersOpenFrom(LocalDateTime.of(TODAY.plusDays(1), LocalTime.of(9, 0)));
        assertThat(OfferingTiming.isWindowOpenNow(product, TODAY.plusDays(1), LocalTime.of(8, 59))).isFalse();
        assertThat(OfferingTiming.lifecycleState(product, TODAY.plusDays(1), LocalTime.of(8, 59)))
                .isEqualTo("SCHEDULED_TO_OPEN");
        assertThat(OfferingTiming.isWindowOpenNow(product, TODAY.plusDays(1), LocalTime.of(9, 0))).isTrue();
    }

    @Test
    void openingTimestampTakesPrecedenceOverLegacyTimeOnlyStart() {
        Product product = product(TODAY, false, "09:00", "23:58", 10);
        product.setOrdersOpenFrom(LocalDateTime.of(TODAY, LocalTime.of(10, 0)));
        assertThat(OfferingTiming.isWindowOpenNow(product, TODAY, LocalTime.of(9, 30))).isFalse();
        assertThat(OfferingTiming.isWindowOpenNow(product, TODAY, LocalTime.of(10, 0))).isTrue();
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
    void isLaterHhmmOnlyAcceptsARealExtension() {
        assertThat(OfferingTiming.isLaterHhmm("22:00", "21:00")).isTrue();
        assertThat(OfferingTiming.isLaterHhmm("21:00", "21:00")).isFalse();
        assertThat(OfferingTiming.isLaterHhmm("20:00", "21:00")).isFalse();
        // Late evening must not wrap around into "earlier" than a morning cutoff.
        assertThat(OfferingTiming.isLaterHhmm("09:00", "22:00")).isFalse();
        assertThat(OfferingTiming.isLaterHhmm("23:59", "00:01")).isTrue();
    }

    @Test
    void isLaterHhmmIsNullSafe() {
        assertThat(OfferingTiming.isLaterHhmm(null, "21:00")).isFalse();
        assertThat(OfferingTiming.isLaterHhmm("22:00", null)).isFalse();
        assertThat(OfferingTiming.isLaterHhmm("22:00", "  ")).isFalse();
    }

    @Test
    void isLaterHhmmRejectsMalformedCutoffs() {
        assertThatThrownBy(() -> OfferingTiming.isLaterHhmm("9:00", "21:00"))
                .hasMessageContaining("24-hour HH:mm");
        assertThatThrownBy(() -> OfferingTiming.isLaterHhmm("22:00", "25:00"))
                .hasMessageContaining("24-hour HH:mm");
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
    void buyerSectionFollowsOrderableDeliveryDateInsteadOfStoredPreorderFlag() {
        Product todayProduct = product(TODAY, true, null, "23:58", 10);
        todayProduct.setAvailableToday(true);
        ProductDto todayDto = buyerDto(todayProduct, "PRE_ORDER", false);
        assertThat(OfferingTiming.buyerSection(todayDto, todayProduct, TODAY)).isEqualTo("ORDER");

        Product futureProduct = product(TODAY.plusDays(1), true, null, "23:58", 10);
        ProductDto futureDto = buyerDto(futureProduct, "PRE_ORDER", false);
        assertThat(futureDto.getAvailableToday()).isFalse();
        assertThat(OfferingTiming.buyerSection(futureDto, futureProduct, TODAY)).isEqualTo("PRE_ORDER");

        futureDto.setLifecycleState("SCHEDULED_TO_OPEN");
        futureDto.setOrdersClosed(true);
        assertThat(OfferingTiming.buyerSection(futureDto, futureProduct, TODAY)).isEqualTo("PRE_ORDER");
        assertThat(OfferingTiming.isBuyerOrderableNow(futureDto, futureProduct, TODAY)).isFalse();
    }

    @Test
    void closedCurrentOfferingRemainsInOrderSectionButIsNotOrderable() {
        Product product = product(TODAY, false, null, "12:00", 10);
        ProductDto dto = buyerDto(product, "ORDER", true);
        assertThat(OfferingTiming.buyerSection(dto, product, TODAY)).isEqualTo("ORDER");
        assertThat(OfferingTiming.isBuyerOrderableNow(dto, product, TODAY)).isFalse();
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

    @Test
    void amToPmAutoCorrectionAppliedWhenCloseInPast() {
        // Now is 14:00 (2:00 PM). Close entered as 03:47 (3:47 AM). Delivery is 16:47 (4:47 PM).
        // +12h makes 15:47, which is in the future (> 14:00) and before delivery (16:47).
        String corrected = OfferingTiming.validateNewOffering(TODAY, false, null, "03:47",
                "2026-09-24T16:47", TODAY, LocalTime.of(14, 0));
        assertThat(corrected).isEqualTo("15:47");
    }

    @Test
    void gracefulClampingToDeliveryMinus45MinutesWhenAmToPmInvalidOrStillPast() {
        // Now is 16:00. Close entered as 03:45 (3:45 AM).
        // +12h makes 15:45, which is STILL before now (16:00).
        // Delivery is 17:00 (5:00 PM).
        // Gracefully clamped to readyByTime minus 45 minutes = 16:15.
        String corrected = OfferingTiming.validateNewOffering(TODAY, false, null, "03:45",
                "2026-09-24T17:00", TODAY, LocalTime.of(16, 0));
        assertThat(corrected).isEqualTo("16:15");
    }

    @Test
    void gracefulClampingToNowPlus15WhenDeliveryIsClose() {
        // Now is 16:30. Close entered as 15:00 (PM, already passed).
        // Delivery is 17:00 (30 mins from now; delivery - 45m = 16:15 is already past).
        // Gracefully clamped to now plus 15 minutes = 16:45.
        String corrected = OfferingTiming.validateNewOffering(TODAY, false, null, "15:00",
                "2026-09-24T17:00", TODAY, LocalTime.of(16, 30));
        assertThat(corrected).isEqualTo("16:45");
    }

    @Test
    void gracefulClampingWhenDeliveryWithin15Minutes() {
        // Now is 16:30. Close entered as 15:00 (past).
        // Delivery is 16:40 (10 mins away).
        // Midpoint between 16:30 and 16:40 is 16:35.
        String corrected = OfferingTiming.validateNewOffering(TODAY, false, null, "15:00",
                "2026-09-24T16:40", TODAY, LocalTime.of(16, 30));
        assertThat(corrected).isEqualTo("16:35");
    }

    @Test
    void preorderValidMorningCutoffOnDeliveryDayPreserved() {
        // Offering is tomorrow. Delivery is tomorrow at 18:00.
        // Close entered as 09:00 (9:00 AM on delivery day, 9h before delivery).
        // Since 09:00 on delivery day is strictly before 18:00 delivery and in the future, it is preserved.
        String validated = OfferingTiming.validateNewOffering(TODAY.plusDays(1), true, null, "09:00",
                "2026-09-25T18:00", TODAY, LocalTime.of(14, 0));
        assertThat(validated).isEqualTo("09:00");
    }

    @Test
    void preorderCloseAtOrAfterDeliveryHealsToOneHourBeforeDelivery() {
        // Offering is tomorrow. Delivery is tomorrow at 18:00.
        // Close entered as 18:00 (not before delivery).
        // Auto-heals to delivery minus 60 minutes = 17:00.
        String validated = OfferingTiming.validateNewOffering(TODAY.plusDays(1), true, null, "18:00",
                "2026-09-25T18:00", TODAY, LocalTime.of(22, 0));
        assertThat(validated).isEqualTo("17:00");
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

    private ProductDto buyerDto(Product product, String lifecycle, boolean ordersClosed) {
        ProductDto dto = new ProductDto();
        dto.setAvailableToday(product.getAvailableToday());
        dto.setOrdersPaused(product.isOrdersPaused());
        dto.setSoldOut(product.isSoldOut());
        dto.setOrdersClosed(ordersClosed);
        dto.setLifecycleState(lifecycle);
        dto.setRecurring(false);
        return dto;
    }
}
