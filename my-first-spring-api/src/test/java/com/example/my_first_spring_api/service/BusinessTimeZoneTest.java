package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Render runs the JVM in UTC but every cut-off, "today" and ready-by time must
 * follow India time. The default zone is pinned once at startup by
 * {@link AppTimeZone}; these tests prove the existing LocalDate/LocalTime.now()
 * based rules then behave in Asia/Kolkata even when the JVM began in UTC.
 */
class BusinessTimeZoneTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    // 20:00 UTC on 24 Sep is already 01:30 on 25 Sep in India.
    private static final Instant NIGHT_INSTANT = Instant.parse("2026-09-24T20:00:00Z");

    private TimeZone originalDefault;

    @BeforeEach
    void startFromUtcLikeTheContainer() {
        originalDefault = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restoreDefault() {
        TimeZone.setDefault(originalDefault);
    }

    @Test
    void applyMovesTheJvmDefaultFromUtcToKolkata() {
        assertThat(ZoneId.systemDefault().getId()).isIn("UTC", "Etc/UTC", "Z");

        AppTimeZone.apply();

        assertThat(ZoneId.systemDefault()).isEqualTo(KOLKATA);
        assertThat(AppTimeZone.ZONE).isEqualTo(KOLKATA);
    }

    @Test
    void nowBasedCallsReadIndiaTimeAfterApply() {
        AppTimeZone.apply();

        LocalDate before = LocalDate.now(KOLKATA);
        LocalDate today = LocalDate.now();
        LocalDate after = LocalDate.now(KOLKATA);
        assertThat(today).isBetween(before, after);

        long driftSeconds = Math.abs(java.time.Duration.between(
                LocalDateTime.now(), LocalDateTime.now(KOLKATA)).getSeconds());
        assertThat(driftSeconds).isLessThan(5);
    }

    @Test
    void applyIsIdempotent() {
        AppTimeZone.apply();
        assertThatCode(AppTimeZone::apply).doesNotThrowAnyException();
        assertThat(ZoneId.systemDefault()).isEqualTo(KOLKATA);
    }

    @Test
    void cutoffAndTodayUseIndiaTimeEvenThoughTheJvmStartedInUtc() {
        Product product = product(LocalDate.of(2026, 9, 25), "01:00");

        // Under UTC it is still 24 Sep 20:00, so a 25 Sep offering is a future one: still open.
        Clock utcClock = Clock.fixed(NIGHT_INSTANT, ZoneId.systemDefault());
        assertThatCode(() -> OfferingTiming.enforceOrderWindow(product, LocalDate.of(2026, 9, 25), "today",
                LocalDate.now(utcClock), LocalTime.now(utcClock))).doesNotThrowAnyException();

        // After startup pinning, the same instant is 25 Sep 01:30 in India: the 01:00 cut-off has passed.
        AppTimeZone.apply();
        Clock indiaClock = Clock.fixed(NIGHT_INSTANT, ZoneId.systemDefault());
        LocalDate today = LocalDate.now(indiaClock);
        LocalTime now = LocalTime.now(indiaClock);
        assertThat(today).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(now).isEqualTo(LocalTime.of(1, 30));
        assertThatThrownBy(() -> OfferingTiming.enforceOrderWindow(product, today, "today", today, now))
                .hasMessageContaining("orders today are closed");
        assertThat(OfferingTiming.isWindowOpenNow(product, today, now)).isFalse();
    }

    private Product product(LocalDate date, String close) {
        User seller = new User("Seller", "9100000001", "A-1", UserRole.SELLER);
        seller.setId(1L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        Product product = new Product(kitchen, "Poha", "d", BigDecimal.ONE, null);
        product.setAvailableDate(date);
        product.setAvailableToday(false);
        product.setIsPreorder(false);
        product.setOrderWindowEnd(close);
        product.setCutoffTime(close);
        product.setMaxQuantity(10);
        product.setRemainingQuantity(10);
        return product;
    }
}
