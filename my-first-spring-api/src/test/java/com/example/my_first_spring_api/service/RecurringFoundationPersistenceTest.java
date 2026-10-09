package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.example.my_first_spring_api.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:recurring-phase1;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false"})
@ActiveProfiles("test")
class RecurringFoundationPersistenceTest {
  @Autowired RecurringScheduleService scheduleService;
  @Autowired UserRepository users; @Autowired KitchenRepository kitchens;
  @Autowired ProductRepository products; @Autowired RecurringScheduleRepository schedules;
  @Autowired OccurrenceRepository occurrences; @Autowired OccurrenceOverrideRepository overrides;
  private User seller; private Kitchen kitchen; private Product product;

  @BeforeEach void setup() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    seller = users.save(new User("Seller" + s, "91" + s, null, UserRole.SELLER));
    seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
    users.save(seller);
    kitchen = kitchens.save(new Kitchen("k" + s, "Kitchen " + s, "", null, seller));
    product = new Product(kitchen, "Poha", "", BigDecimal.valueOf(25), null);
    product.setAvailableToday(true);
    product.setRemainingQuantity(50);
    product = products.save(product);
  }

  private RecurringSchedule newSchedule(Product p, Integer qty) {
    RecurringSchedule sch = new RecurringSchedule(p, LocalDate.of(2026, 10, 6),
        LocalDate.of(2026, 11, 5), EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
    sch.setDefaultQuantity(qty);
    sch.setDefaultOrderCloseTime("13:00");
    sch.setDefaultReadyByTime("3:00 PM");
    return sch;
  }

  @Test void schedulePersistsAgainstProduct() {
    RecurringSchedule saved = schedules.save(newSchedule(product, 14));
    assertThat(saved.getId()).isNotNull();
    assertThat(schedules.findByProductId(product.getId())).isPresent();
  }

  @Test void scheduleSupportsMultipleWeekdays() {
    RecurringSchedule saved = schedules.save(newSchedule(product, 14));
    assertThat(saved.getRecurrenceDays()).isEqualTo("1,3,5");
    assertThat(saved.getRecurrenceWeekdays())
        .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
  }

  @Test void scheduleSupportsFiniteAndNoLimitQuantity() {
    RecurringSchedule finite = schedules.save(newSchedule(product, 14));
    assertThat(finite.getDefaultQuantity()).isEqualTo(14);
    assertThat(finite.isNoLimit()).isFalse();
  }

  @Test void scheduleSupportsNullNoLimitQuantity() {
    RecurringSchedule nolimit = schedules.save(newSchedule(product, null));
    RecurringSchedule reloaded = schedules.findById(nolimit.getId()).orElseThrow();
    assertThat(reloaded.getDefaultQuantity()).isNull();
    assertThat(reloaded.isNoLimit()).isTrue();
  }

  @Test void ongoingFactoryCapsWindowAt90Days() {
    LocalDate start = LocalDate.of(2026, 10, 6);
    RecurringSchedule sch = RecurringSchedule.ongoing(product, start,
        EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
    sch.setDefaultQuantity(14);
    RecurringSchedule saved = schedules.save(sch);
    assertThat(saved.getEndDate()).isEqualTo(start.plusDays(89));
    assertThat(java.time.temporal.ChronoUnit.DAYS.between(saved.getStartDate(), saved.getEndDate()) + 1)
        .isEqualTo(90);
  }

  @Test void scheduleStoresDefaultTimes() {
    RecurringSchedule saved = schedules.save(newSchedule(product, 14));
    assertThat(saved.getDefaultOrderCloseTime()).isEqualTo("13:00");
    assertThat(saved.getDefaultReadyByTime()).isEqualTo("3:00 PM");
  }

  @Test void scheduleSupportsBoundedWindowAndOngoingCap() {
    RecurringSchedule saved = schedules.save(newSchedule(product, 14));
    assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    RecurringSchedule on = RecurringSchedule.ongoing(product, LocalDate.of(2026, 10, 6),
        EnumSet.of(DayOfWeek.MONDAY));
    assertThat(on.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 6).plusDays(89));
    assertThat(RecurringSchedule.ONGOING_INTERNAL_DAYS_CAP).isEqualTo(90);
  }

  @Test void scheduleStatusDefaultsActive() {
    RecurringSchedule saved = schedules.save(newSchedule(product, 14));
    assertThat(saved.getStatus()).isEqualTo(RecurringScheduleStatus.ACTIVE);
  }

  @Test void occurrencesPersistIndependentlyPerDate() {
    RecurringSchedule sch = schedules.save(newSchedule(product, 14));
    Occurrence mon = new Occurrence(sch, LocalDate.of(2026, 10, 12));
    mon.setQuantity(14); mon.setOrderCloseTime("13:00"); mon.setReadyByTime("3:00 PM");
    Occurrence wed = new Occurrence(sch, LocalDate.of(2026, 10, 14));
    wed.setQuantity(14); wed.setOrderCloseTime("13:00"); wed.setReadyByTime("3:00 PM");
    Occurrence fri = new Occurrence(sch, LocalDate.of(2026, 10, 16));
    occurrences.save(mon); occurrences.save(wed); occurrences.save(fri);
    assertThat(occurrences.findByScheduleIdOrderByOccurrenceDateAsc(sch.getId())).hasSize(3);
    assertThat(occurrences.findByScheduleIdAndOccurrenceDate(sch.getId(),
        LocalDate.of(2026, 10, 14))).isPresent();
  }

  @Test void duplicateOccurrenceDateRejected() {
    RecurringSchedule sch = schedules.save(newSchedule(product, 14));
    occurrences.saveAndFlush(new Occurrence(sch, LocalDate.of(2026, 10, 14)));
    assertThatThrownBy(() -> occurrences.saveAndFlush(new Occurrence(sch, LocalDate.of(2026, 10, 14))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test void occurrenceStoresPerDateState() {
    RecurringSchedule sch = schedules.save(newSchedule(product, 14));
    Occurrence wed = new Occurrence(sch, LocalDate.of(2026, 10, 14));
    wed.setQuantity(25); wed.setOrderCloseTime("14:00"); wed.setReadyByTime("2:00 PM");
    wed.setStatus(OccurrenceStatus.LIVE);
    Occurrence saved = occurrences.save(wed);
    assertThat(saved.getStatus()).isEqualTo(OccurrenceStatus.LIVE);
    assertThat(saved.getQuantity()).isEqualTo(25);
  }

  @Test void overrideBelongsToOneOccurrenceAndLeavesScheduleDefaults() {
    RecurringSchedule sch = schedules.save(newSchedule(product, 14));
    Occurrence mon = occurrences.save(new Occurrence(sch, LocalDate.of(2026, 10, 12)));
    Occurrence wed = occurrences.save(new Occurrence(sch, LocalDate.of(2026, 10, 14)));
    Occurrence fri = occurrences.save(new Occurrence(sch, LocalDate.of(2026, 10, 16)));
    OccurrenceOverride ov = new OccurrenceOverride(wed);
    ov.setQuantity(25); ov.setOrderCloseTime("14:00"); ov.setReadyByTime("2:00 PM");
    overrides.save(ov);
    assertThat(overrides.findByOccurrenceId(wed.getId())).isPresent();
    assertThat(overrides.findByOccurrenceId(mon.getId())).isEmpty();
    assertThat(overrides.findByOccurrenceId(fri.getId())).isEmpty();
    // Schedule defaults untouched.
    RecurringSchedule reloaded = schedules.findById(sch.getId()).orElseThrow();
    assertThat(reloaded.getDefaultQuantity()).isEqualTo(14);
    assertThat(reloaded.getDefaultOrderCloseTime()).isEqualTo("13:00");
    assertThat(reloaded.getDefaultReadyByTime()).isEqualTo("3:00 PM");
    // Only the Wednesday occurrence carries an override row.
    assertThat(overrides.findByOccurrenceId(wed.getId()).orElseThrow().getQuantity()).isEqualTo(25);
  }

  @Test void overrideSupportsNullMeaningNotOverridden() {
    RecurringSchedule sch = schedules.save(newSchedule(product, 14));
    Occurrence wed = occurrences.save(new Occurrence(sch, LocalDate.of(2026, 10, 14)));
    OccurrenceOverride ov = new OccurrenceOverride(wed);
    ov.setQuantity(25); // close/ready/soldOut/ordersPaused stay null
    OccurrenceOverride saved = overrides.save(ov);
    assertThat(saved.getQuantity()).isEqualTo(25);
    assertThat(saved.getOrderCloseTime()).isNull();
    assertThat(saved.getReadyByTime()).isNull();
    assertThat(saved.getSoldOut()).isNull();
    assertThat(saved.getOrdersPaused()).isNull();
  }

  @Test void secondOverrideForSameOccurrenceRejected() {
    RecurringSchedule sch = schedules.save(newSchedule(product, 14));
    Occurrence wed = occurrences.save(new Occurrence(sch, LocalDate.of(2026, 10, 14)));
    overrides.saveAndFlush(new OccurrenceOverride(wed));
    assertThatThrownBy(() -> overrides.saveAndFlush(new OccurrenceOverride(wed)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void acceptanceScenario() {
    // 1. Create schedule
    RecurringSchedule sch = schedules.save(newSchedule(product, 14));
    // Simulate generation for dates: 10/12 (Mon), 10/14 (Wed), 10/16 (Fri)
    Occurrence mon = occurrences.save(new Occurrence(sch, LocalDate.of(2026, 10, 12)));
    Occurrence wed = occurrences.save(new Occurrence(sch, LocalDate.of(2026, 10, 14)));
    Occurrence fri = occurrences.save(new Occurrence(sch, LocalDate.of(2026, 10, 16)));
    
    // Default setting for occurrences
    for (Occurrence o : java.util.List.of(mon, wed, fri)) {
        o.setQuantity(14); o.setOrderCloseTime("10:00"); o.setReadyByTime("13:00");
        occurrences.save(o);
    }

    // 2. Wednesday override
    OccurrenceOverride wedOv = new OccurrenceOverride(wed);
    wedOv.setQuantity(25); wedOv.setReadyByTime("14:00");
    overrides.save(wedOv);
    
    // Verify overrides
    assertThat(occurrences.findById(wed.getId()).get().getQuantity()).isEqualTo(14); // Entity not updated yet?
    // Resolved Occurrence Dto would show 25/10AM/2PM
    OccurrenceDto wedDto = scheduleService.getResolvedOccurrence(wed.getId());
    assertThat(wedDto.getQuantity()).isEqualTo(25);
    assertThat(wedDto.getReadyByTime()).isEqualTo("14:00");
    assertThat(wedDto.getOrderCloseTime()).isEqualTo("10:00");

    // Exact V2 §6 scenario: Monday and Friday are UNAFFECTED by Wednesday's
    // override - they keep the original 14 plates / 1:00 PM defaults - and
    // the recurring schedule itself still stores the original defaults.
    OccurrenceDto monBefore = scheduleService.getResolvedOccurrence(mon.getId());
    assertThat(monBefore.getQuantity()).isEqualTo(14);
    assertThat(monBefore.getReadyByTime()).isEqualTo("13:00");
    OccurrenceDto friBefore = scheduleService.getResolvedOccurrence(fri.getId());
    assertThat(friBefore.getQuantity()).isEqualTo(14);
    assertThat(friBefore.getReadyByTime()).isEqualTo("13:00");
    assertThat(friBefore.getOrderCloseTime()).isEqualTo("10:00");
    RecurringSchedule reloaded = schedules.findById(sch.getId()).orElseThrow();
    assertThat(reloaded.getDefaultQuantity()).isEqualTo(14);
    assertThat(reloaded.getDefaultReadyByTime()).isEqualTo("3:00 PM");

    // 3. Change future schedule defaults
    RecurringScheduleDto updateDto = new RecurringScheduleDto();
    updateDto.setStartDate(sch.getStartDate());
    updateDto.setEndDate(sch.getEndDate());
    updateDto.setRecurrenceWeekdays(sch.getRecurrenceWeekdays());
    updateDto.setDefaultQuantity(18);
    updateDto.setDefaultOrderCloseTime("10:00");
    updateDto.setDefaultReadyByTime("13:30");
    
    scheduleService.updateSchedule(sch.getId(), updateDto);
    
    // Verify future occurrences updated
    // Note: Assuming "today" in updateSchedule is before 10/12, all are future.
    assertThat(occurrences.findById(mon.getId()).get().getQuantity()).isEqualTo(18);
    assertThat(occurrences.findById(mon.getId()).get().getReadyByTime()).isEqualTo("13:30");
    
    // Verify Wednesday override remains
    OccurrenceDto wedDtoAfterUpdate = scheduleService.getResolvedOccurrence(wed.getId());
    assertThat(wedDtoAfterUpdate.getQuantity()).isEqualTo(25); // Overridden
    assertThat(wedDtoAfterUpdate.getReadyByTime()).isEqualTo("14:00"); // Overridden
    
    // Verify Monday (un-overridden) updated
    OccurrenceDto monDto = scheduleService.getResolvedOccurrence(mon.getId());
    assertThat(monDto.getQuantity()).isEqualTo(18);
    assertThat(monDto.getReadyByTime()).isEqualTo("13:30");

    // After an intentional Manage Schedule edit, Friday (un-overridden)
    // follows the NEW defaults while Wednesday keeps its override - never a
    // silent rewrite of an edited occurrence (V2 §9 rules).
    OccurrenceDto friAfter = scheduleService.getResolvedOccurrence(fri.getId());
    assertThat(friAfter.getQuantity()).isEqualTo(18);
    assertThat(friAfter.getReadyByTime()).isEqualTo("13:30");
    OccurrenceDto wedFinal = scheduleService.getResolvedOccurrence(wed.getId());
    assertThat(wedFinal.getQuantity()).isEqualTo(25);
    assertThat(wedFinal.getOrderCloseTime()).isEqualTo("10:00");
    assertThat(wedFinal.getReadyByTime()).isEqualTo("14:00");
  }

  @Test void pohaAcceptanceOverrideFutureDefaultsPastHistoryAndEndAreIsolated() {
    LocalDate today = LocalDate.now();
    LocalDate start = today.minusWeeks(2).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate end = today.plusWeeks(3);
    Set<DayOfWeek> mwf = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    RecurringSchedule schedule = scheduleService.createSchedule(product, start, end, mwf,
        14, "10:00", "13:00", false);
    int generated = occurrences.findByScheduleId(schedule.getId()).size();
    scheduleService.generateOccurrences(schedule);
    assertThat(occurrences.findByScheduleId(schedule.getId())).hasSize(generated);

    LocalDate wednesday = today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
    Occurrence wed = occurrences.findByScheduleIdAndOccurrenceDate(schedule.getId(), wednesday).orElseThrow();
    scheduleService.updateOccurrenceOverride(wed.getId(), 25, null, "14:00", null, null, null);
    OccurrenceDto resolvedWed = scheduleService.getResolvedOccurrence(wed.getId());
    assertThat(resolvedWed.getQuantity()).isEqualTo(25);
    assertThat(resolvedWed.getOrderCloseTime()).isEqualTo("10:00");
    assertThat(resolvedWed.getReadyByTime()).isEqualTo("14:00");

    Occurrence past = occurrences.findByScheduleIdOrderByOccurrenceDateAsc(schedule.getId()).stream()
        .filter(o -> o.getOccurrenceDate().isBefore(today)).findFirst().orElseThrow();
    Integer pastQuantity = past.getQuantity();
    String pastReady = past.getReadyByTime();

    RecurringScheduleDto update = new RecurringScheduleDto();
    update.setStartDate(start);
    update.setEndDate(end);
    update.setRecurrenceWeekdays(mwf);
    update.setDefaultQuantity(18);
    update.setDefaultOrderCloseTime("10:00");
    update.setDefaultReadyByTime("13:30");
    update.setOngoing(false);
    scheduleService.updateSchedule(schedule.getId(), update);

    LocalDate monday = wednesday.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
    LocalDate friday = wednesday.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.FRIDAY));
    OccurrenceDto resolvedMonday = scheduleService.resolveOccurrenceOn(schedule.getId(), monday);
    OccurrenceDto resolvedFriday = scheduleService.resolveOccurrenceOn(schedule.getId(), friday);
    assertThat(resolvedMonday.getQuantity()).isEqualTo(18);
    assertThat(resolvedMonday.getOrderCloseTime()).isEqualTo("10:00");
    assertThat(resolvedMonday.getReadyByTime()).isEqualTo("13:30");
    assertThat(resolvedFriday.getQuantity()).isEqualTo(18);
    assertThat(resolvedFriday.getReadyByTime()).isEqualTo("13:30");
    assertThat(scheduleService.getResolvedOccurrence(wed.getId()).getQuantity()).isEqualTo(25);
    assertThat(scheduleService.getResolvedOccurrence(wed.getId()).getOrderCloseTime()).isEqualTo("10:00");
    assertThat(scheduleService.getResolvedOccurrence(wed.getId()).getReadyByTime()).isEqualTo("14:00");
    Occurrence historical = occurrences.findById(past.getId()).orElseThrow();
    assertThat(historical.getQuantity()).isEqualTo(pastQuantity);
    assertThat(historical.getReadyByTime()).isEqualTo(pastReady);

    scheduleService.endSchedule(schedule.getId());
    assertThat(schedules.findById(schedule.getId()).orElseThrow().getStatus()).isEqualTo(RecurringScheduleStatus.ENDED);
    assertThat(occurrences.findByScheduleId(schedule.getId())).hasSize(generated);
    assertThat(overrides.findByOccurrenceId(wed.getId())).isPresent();
  }

  @Test void identicalCreationIsRetrySafeButConflictingCreationIsRejected() {
    LocalDate start = LocalDate.now().plusDays(1);
    LocalDate end = start.plusDays(14);
    Set<DayOfWeek> days = EnumSet.of(start.getDayOfWeek());
    long scheduleCountBefore = schedules.count();
    RecurringSchedule first = scheduleService.createSchedule(product, start, end, days,
        14, "13:00", "3:00 PM");
    RecurringSchedule retry = scheduleService.createSchedule(product, start, end, days,
        14, "13:00", "3:00 PM");

    assertThat(retry.getId()).isEqualTo(first.getId());
    assertThat(schedules.count()).isEqualTo(scheduleCountBefore + 1);
    assertThatThrownBy(() -> scheduleService.createSchedule(product, start, end, days,
        15, "13:00", "3:00 PM"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("different");
  }

  @Test void occurrenceGenerationIsIdempotent() {
    LocalDate start = LocalDate.now().plusDays(1);
    RecurringSchedule schedule = scheduleService.createSchedule(product, start, start.plusDays(14),
        EnumSet.allOf(DayOfWeek.class), 10, "13:00", "3:00 PM");
    int firstCount = occurrences.findByScheduleId(schedule.getId()).size();

    scheduleService.generateOccurrences(schedule);
    scheduleService.generateOccurrences(schedule);

    assertThat(occurrences.findByScheduleId(schedule.getId())).hasSize(firstCount);
  }

  @Test void quantityOverrideDoesNotFreezeOtherDefaultsAndCanBeExplicitlyUnlimited() {
    LocalDate start = LocalDate.now().plusDays(1);
    RecurringSchedule schedule = scheduleService.createSchedule(product, start, start.plusDays(2),
        EnumSet.allOf(DayOfWeek.class), 10, "13:00", "3:00 PM");
    Occurrence occurrence = occurrences.findByScheduleIdOrderByOccurrenceDateAsc(schedule.getId()).get(0);
    scheduleService.updateOccurrenceOverride(occurrence.getId(), 25, null, null);

    RecurringScheduleDto update = new RecurringScheduleDto();
    update.setDefaultQuantity(12);
    update.setDefaultOrderCloseTime("14:00");
    update.setDefaultReadyByTime("4:00 PM");
    scheduleService.updateSchedule(schedule.getId(), update);

    OccurrenceDto resolved = scheduleService.getResolvedOccurrence(occurrence.getId());
    assertThat(resolved.getQuantity()).isEqualTo(25);
    assertThat(resolved.getOrderCloseTime()).isEqualTo("14:00");
    assertThat(resolved.getReadyByTime()).isEqualTo("4:00 PM");

    scheduleService.updateOccurrenceOverride(occurrence.getId(), null, null, null,
        null, null, true);
    assertThat(scheduleService.getResolvedOccurrence(occurrence.getId()).getQuantity()).isNull();
  }

  @Test void emptyOccurrencePatchDoesNotCreateOverride() {
    LocalDate start = LocalDate.now().plusDays(1);
    RecurringSchedule schedule = scheduleService.createSchedule(product, start, start,
        EnumSet.of(start.getDayOfWeek()), 10, "13:00", "3:00 PM");
    Occurrence occurrence = occurrences.findByScheduleId(schedule.getId()).get(0);

    scheduleService.updateOccurrenceOverride(occurrence.getId(), null, null, null,
        null, null, null);

    assertThat(overrides.findByOccurrenceId(occurrence.getId())).isEmpty();
  }

  @Test void endedScheduleRejectsUpdatesExtensionsAndFurtherGeneration() {
    LocalDate start = LocalDate.now().plusDays(1);
    RecurringSchedule schedule = scheduleService.createSchedule(product, start, start.plusDays(7),
        EnumSet.allOf(DayOfWeek.class), 10, "13:00", "3:00 PM", true);
    scheduleService.endSchedule(schedule.getId());
    int count = occurrences.findByScheduleId(schedule.getId()).size();

    assertThatThrownBy(() -> scheduleService.updateSchedule(schedule.getId(), new RecurringScheduleDto()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Ended");
    assertThatThrownBy(() -> scheduleService.extendOngoingSchedule(schedule.getId()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Ended");
    scheduleService.generateOccurrences(schedule);
    assertThat(occurrences.findByScheduleId(schedule.getId())).hasSize(count);
  }

  @Test void updateRejectsPastReplacementStart() {
    LocalDate start = LocalDate.now().plusDays(2);
    RecurringSchedule schedule = scheduleService.createSchedule(product, start, start.plusDays(7),
        EnumSet.allOf(DayOfWeek.class), 10, "13:00", "3:00 PM");
    RecurringScheduleDto update = new RecurringScheduleDto();
    update.setStartDate(LocalDate.now().minusDays(1));

    assertThatThrownBy(() -> scheduleService.updateSchedule(schedule.getId(), update))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("past");
  }

  @Test void ongoingCreationUsesServerHorizonAndExtensionIsIdempotent() {
    LocalDate start = LocalDate.now().minusDays(89);
    RecurringSchedule schedule = scheduleService.createSchedule(product, start, LocalDate.now().plusYears(5),
        EnumSet.allOf(DayOfWeek.class), 10, "13:00", "3:00 PM", true);
    assertThat(schedule.isOngoing()).isTrue();
    assertThat(schedule.getEndDate()).isEqualTo(start.plusDays(89));

    RecurringSchedule extended = scheduleService.extendOngoingSchedule(schedule.getId());
    int count = occurrences.findByScheduleId(schedule.getId()).size();
    assertThat(extended.getEndDate()).isEqualTo(LocalDate.now().plusDays(89));
    scheduleService.extendOngoingSchedule(schedule.getId());
    assertThat(occurrences.findByScheduleId(schedule.getId())).hasSize(count);
  }

  @Test void recurrenceChangesRetainRowsAndOverridesButRetireRemovedFutureDates() {
    LocalDate start = LocalDate.now().minusDays(1);
    RecurringSchedule schedule = scheduleService.createSchedule(product, start, LocalDate.now().plusDays(3),
        EnumSet.allOf(DayOfWeek.class), 10, "13:00", "3:00 PM");
    java.util.List<Occurrence> before = occurrences.findByScheduleIdOrderByOccurrenceDateAsc(schedule.getId());
    Occurrence removedFuture = before.stream()
        .filter(o -> o.getOccurrenceDate().isAfter(LocalDate.now())).findFirst().orElseThrow();
    scheduleService.updateOccurrenceOverride(removedFuture.getId(), 25, null, null);

    RecurringScheduleDto update = new RecurringScheduleDto();
    update.setRecurrenceWeekdays(EnumSet.of(LocalDate.now().getDayOfWeek()));
    scheduleService.updateSchedule(schedule.getId(), update);

    assertThat(occurrences.findByScheduleId(schedule.getId())).hasSize(before.size());
    assertThat(occurrences.findById(removedFuture.getId()).orElseThrow().getStatus())
        .isEqualTo(OccurrenceStatus.ENDED);
    assertThat(overrides.findByOccurrenceId(removedFuture.getId())).isPresent();
    assertThat(occurrences.findByScheduleId(schedule.getId()))
        .anyMatch(o -> o.getOccurrenceDate().equals(start));
  }
}
