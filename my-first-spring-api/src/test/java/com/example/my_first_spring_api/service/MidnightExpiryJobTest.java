package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MidnightExpiryJob: occurrence expiry and activation logic.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>Past occurrences marked ENDED idempotently.</li>
 *   <li>Missing occurrences generated for active schedules.</li>
 *   <li>Idempotent: existing rows not duplicated.</li>
 *   <li>Correct timezone (Asia/Kolkata) for date boundaries.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:midnight-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
@Transactional
class MidnightExpiryJobTest {

    @Autowired private MidnightExpiryJob midnightExpiryJob;
    @Autowired private RecurringScheduleRepository scheduleRepository;
    @Autowired private OccurrenceRepository occurrenceRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private UserRepository userRepository;

    private User seller;
    private Kitchen kitchen;
    private Product recurringProduct;
    private RecurringSchedule schedule;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.saveAndFlush(seller);

        kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen = kitchenRepository.saveAndFlush(kitchen);

        recurringProduct = new Product(kitchen, "Daily Lunch", "", new BigDecimal("100.00"), null);
        recurringProduct.setAvailableToday(true);
        recurringProduct.setMaxQuantity(20);
        recurringProduct.setRemainingQuantity(20);
        recurringProduct.setPriceUnit("portion");
        recurringProduct.setCategory("LUNCH");
        recurringProduct = productRepository.saveAndFlush(recurringProduct);

LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalDate startDate = today.minusDays(1);
        LocalDate endDate = today.plusDays(1); // Only today and tomorrow
        // Only today's and tomorrow's weekdays
        EnumSet<DayOfWeek> weekdays = EnumSet.of(today.getDayOfWeek(), today.plusDays(1).getDayOfWeek());

        schedule = new RecurringSchedule();
        schedule.setProduct(recurringProduct);
        schedule.setStartDate(startDate);
        schedule.setEndDate(endDate);
        schedule.setRecurrenceWeekdays(weekdays); // Only today's and tomorrow's weekdays
        schedule.setDefaultQuantity(10);
        schedule.setDefaultOrderCloseTime("23:00");
        schedule.setDefaultReadyByTime("12:00");
        schedule.setStatus(RecurringScheduleStatus.ACTIVE);
        schedule.setOngoing(true);
        schedule = scheduleRepository.saveAndFlush(schedule);
    }

    private int countOccurrencesForSchedule(Long scheduleId) {
        return occurrenceRepository.findByScheduleId(scheduleId).size();
    }

    @Test
    void expirePastOccurrencesMarksThemEnded() {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalDate yesterday = today.minusDays(1);
        LocalDate dayBefore = today.minusDays(2);

        Occurrence occ1 = new Occurrence(schedule, yesterday);
        occ1.setQuantity(10);
        occ1.setOrderCloseTime("23:00");
        occ1.setReadyByTime("12:00");
        occ1.setStatus(OccurrenceStatus.SCHEDULED);
        occ1 = occurrenceRepository.saveAndFlush(occ1);

        Occurrence occ2 = new Occurrence(schedule, dayBefore);
        occ2.setQuantity(10);
        occ2.setOrderCloseTime("23:00");
        occ2.setReadyByTime("12:00");
        occ2.setStatus(OccurrenceStatus.LIVE);
        occ2 = occurrenceRepository.saveAndFlush(occ2);

        midnightExpiryJob.expireAndActivateOccurrences();

        Occurrence updated1 = occurrenceRepository.findById(occ1.getId()).orElseThrow();
        Occurrence updated2 = occurrenceRepository.findById(occ2.getId()).orElseThrow();

        assertThat(updated1.getStatus()).isEqualTo(OccurrenceStatus.ENDED);
        assertThat(updated2.getStatus()).isEqualTo(OccurrenceStatus.ENDED);
    }

    @Test
    void expirePastOccurrencesIsIdempotent() {
        LocalDate yesterday = LocalDate.now(AppTimeZone.ZONE).minusDays(1);

        Occurrence occ = new Occurrence(schedule, yesterday);
        occ.setQuantity(10);
        occ.setOrderCloseTime("23:00");
        occ.setReadyByTime("12:00");
        occ.setStatus(OccurrenceStatus.ENDED);
        occ = occurrenceRepository.saveAndFlush(occ);

        LocalDateTime originalUpdatedAt = occ.getUpdatedAt();

        midnightExpiryJob.expireAndActivateOccurrences();

        Occurrence updated = occurrenceRepository.findById(occ.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OccurrenceStatus.ENDED);
    }

    @Test
    void activateScheduledOccurrencesGeneratesMissingRows() {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalDate tomorrow = today.plusDays(1);

        long before = countOccurrencesForSchedule(schedule.getId());
        assertThat(before).isEqualTo(0);

        midnightExpiryJob.expireAndActivateOccurrences();

        List<Occurrence> occurrences = occurrenceRepository.findByScheduleId(schedule.getId());
        assertThat(occurrences).hasSize(2);

        LocalDate[] expectedDates = {today, tomorrow};
        for (LocalDate expected : expectedDates) {
            Occurrence found = occurrences.stream()
                    .filter(o -> o.getOccurrenceDate().equals(expected))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing occurrence for " + expected));
            assertThat(found.getQuantity()).isEqualTo(10);
            assertThat(found.getOrderCloseTime()).isEqualTo("23:00");
            assertThat(found.getReadyByTime()).isEqualTo("12:00");
            assertThat(found.getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED);
        }
    }

    @Test
    void activateScheduledOccurrencesIsIdempotent() {
        midnightExpiryJob.expireAndActivateOccurrences();
        int afterFirst = countOccurrencesForSchedule(schedule.getId());

        midnightExpiryJob.expireAndActivateOccurrences();
        int afterSecond = countOccurrencesForSchedule(schedule.getId());

        assertThat(afterSecond).isEqualTo(afterFirst);
    }

    @Test
    void fullJobRunHandlesBothExpiryAndActivation() {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        Occurrence past = new Occurrence(schedule, yesterday);
        past.setQuantity(10);
        past.setOrderCloseTime("23:00");
        past.setReadyByTime("12:00");
        past.setStatus(OccurrenceStatus.LIVE);
        occurrenceRepository.saveAndFlush(past);

        midnightExpiryJob.expireAndActivateOccurrences();

        Occurrence updatedPast = occurrenceRepository.findById(past.getId()).orElseThrow();
        assertThat(updatedPast.getStatus()).isEqualTo(OccurrenceStatus.ENDED);

        List<Occurrence> all = occurrenceRepository.findByScheduleId(schedule.getId());
        assertThat(all).hasSize(3);

        long endedCount = all.stream().filter(o -> o.getStatus() == OccurrenceStatus.ENDED).count();
        long scheduledCount = all.stream().filter(o -> o.getStatus() == OccurrenceStatus.SCHEDULED).count();

        assertThat(endedCount).isEqualTo(1);
        assertThat(scheduledCount).isEqualTo(2);
    }

    @Test
    void onlyActiveSchedulesGenerateOccurrences() {
        Product product2 = new Product(kitchen, "Ended Product", "", new BigDecimal("50.00"), null);
        product2.setAvailableToday(true);
        product2.setMaxQuantity(10);
        product2.setRemainingQuantity(10);
        product2.setPriceUnit("portion");
        product2.setCategory("DINNER");
        product2 = productRepository.saveAndFlush(product2);

        RecurringSchedule endedSchedule = new RecurringSchedule();
        endedSchedule.setProduct(product2);
        endedSchedule.setStartDate(LocalDate.now(AppTimeZone.ZONE).minusDays(5));
        endedSchedule.setEndDate(LocalDate.now(AppTimeZone.ZONE).minusDays(1));
        endedSchedule.setRecurrenceWeekdays(EnumSet.allOf(java.time.DayOfWeek.class));
        endedSchedule.setDefaultQuantity(5);
        endedSchedule.setDefaultOrderCloseTime("22:00");
        endedSchedule.setDefaultReadyByTime("11:00");
        endedSchedule.setStatus(RecurringScheduleStatus.ENDED);
        endedSchedule.setOngoing(false);
        scheduleRepository.saveAndFlush(endedSchedule);

        midnightExpiryJob.expireAndActivateOccurrences();

        int activeScheduleOccurrences = countOccurrencesForSchedule(schedule.getId());
        int endedScheduleOccurrences = countOccurrencesForSchedule(endedSchedule.getId());

        assertThat(activeScheduleOccurrences).isGreaterThan(0);
        assertThat(endedScheduleOccurrences).isEqualTo(0);
    }

    @Test
    void jobUsesAsiaKolkataTimezone() {
        ZoneId systemZone = ZoneId.systemDefault();
        assertThat(systemZone).isEqualTo(ZoneId.of("Asia/Kolkata"));

        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalDate systemToday = LocalDate.now();

        assertThat(today).isEqualTo(systemToday);
    }
}