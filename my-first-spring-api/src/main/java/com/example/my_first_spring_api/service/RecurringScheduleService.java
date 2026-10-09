package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.example.my_first_spring_api.dto.RecurringScheduleDto;
import com.example.my_first_spring_api.dto.OccurrenceDto;
import com.example.my_first_spring_api.dto.ScheduleCardDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class RecurringScheduleService {
    private final RecurringScheduleRepository schedules;
    private final OccurrenceRepository occurrences;
    private final OccurrenceOverrideRepository overrides;
    private final ProductRepository products;
    private final OrderItemRepository orderItems;
    private final EntityManager entityManager;

    @Autowired
    public RecurringScheduleService(RecurringScheduleRepository schedules,
                                     OccurrenceRepository occurrences,
                                     OccurrenceOverrideRepository overrides,
                                     ProductRepository products,
                                     OrderItemRepository orderItems,
                                     EntityManager entityManager) {
        this.schedules = schedules;
        this.occurrences = occurrences;
        this.overrides = overrides;
        this.products = products;
        this.orderItems = orderItems;
        this.entityManager = entityManager;
    }

    /**
     * Creates a schedule for ONE existing product and materialises one
     * occurrence row per matching weekday in [startDate, endDate].
     *
     * <p>Validation mirrors the one-time offering rules where they apply: the
     * range must be ordered, at least one weekday is required, the quantity
     * follows the blank=null convention, and HH:mm close text is checked when
     * present (the ready-by text keeps the legacy free-text contract).
     */
    public RecurringSchedule createSchedule(Product product, LocalDate startDate, LocalDate endDate, Set<DayOfWeek> weekdays,
                                             Integer qty, String orderClose, String readyBy) {
        return createSchedule(product, startDate, endDate, weekdays, qty, orderClose, readyBy, false);
    }

    public RecurringSchedule createSchedule(Product product, LocalDate startDate, LocalDate endDate, Set<DayOfWeek> weekdays,
                                             Integer qty, String orderClose, String readyBy, boolean ongoing) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("A saved product is required to create a recurring schedule.");
        }
        Product lockedProduct = entityManager.find(Product.class, product.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (lockedProduct == null) {
            throw new IllegalArgumentException("A saved product is required to create a recurring schedule.");
        }
        if (startDate == null || (!ongoing && endDate == null)) {
            throw new IllegalArgumentException("Schedule start and end dates are required.");
        }
        LocalDate effectiveEnd = ongoing
                ? startDate.plusDays(RecurringSchedule.ONGOING_INTERNAL_DAYS_CAP - 1L) : endDate;
        if (effectiveEnd.isBefore(startDate)) {
            throw new IllegalArgumentException("Schedule end date cannot be before the start date.");
        }
        if (weekdays == null || weekdays.isEmpty()) {
            throw new IllegalArgumentException("recurrence weekdays must contain at least one day");
        }
        if (qty != null && qty <= 0) {
            throw new IllegalArgumentException("Quantity Available must be at least 1; leave blank for unlimited.");
        }
        String close = orderClose == null ? null : orderClose.trim();
        if (close != null && !close.isEmpty() && !close.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException("Orders Close must use 24-hour HH:mm format, e.g. 08:30");
        }
        if (close != null && close.isEmpty()) close = null;
        String ready = readyBy == null ? null : readyBy.trim();
        if (ready != null && ready.isEmpty()) ready = null;
        ensureMatchingDate(startDate, effectiveEnd, weekdays);
        RecurringSchedule existing = schedules.findLockedByProductId(lockedProduct.getId()).orElse(null);
        if (existing != null) {
            if (sameConfiguration(existing, startDate, effectiveEnd, weekdays, qty, close, ready, ongoing)) {
                generateOccurrences(existing);
                return existing;
            }
            throw new IllegalStateException("This offering already has a different recurring schedule configuration.");
        }
        RecurringSchedule schedule = ongoing
                ? RecurringSchedule.ongoing(lockedProduct, startDate, weekdays)
                : new RecurringSchedule(lockedProduct, startDate, effectiveEnd, weekdays);
        schedule.setDefaultQuantity(qty);
        schedule.setDefaultOrderCloseTime(close);
        schedule.setDefaultReadyByTime(ready);
        schedule.setOngoing(ongoing);
        RecurringSchedule saved = schedules.saveAndFlush(schedule);

        generateOccurrences(saved);
        return saved;
    }

    public RecurringSchedule createOwnedSchedule(Long productId, RecurringScheduleDto dto, Long sellerId) {
        Product product = products.findById(productId).orElseThrow();
        Long ownerId = product.getKitchen() != null && product.getKitchen().getSeller() != null
                ? product.getKitchen().getSeller().getId() : null;
        if (ownerId == null || !ownerId.equals(sellerId)) {
            throw new com.example.my_first_spring_api.exception.SellerNotAuthorizedException(
                    "You do not own this kitchen");
        }
        return createSchedule(product, dto.getStartDate(), dto.getEndDate(), dto.getRecurrenceWeekdays(),
                dto.getDefaultQuantity(), dto.getDefaultOrderCloseTime(), dto.getDefaultReadyByTime(),
                Boolean.TRUE.equals(dto.getOngoing()));
    }

    /**
     * Materialises one occurrence row per matching weekday. Skips dates that
     * already have a row, so a retry or re-save can never violate the
     * (schedule, date) uniqueness that keeps each selling date independent.
     */
    public void generateOccurrences(RecurringSchedule schedule) {
        if (schedule == null || schedule.getId() == null) {
            throw new IllegalArgumentException("A saved schedule is required to generate occurrences.");
        }
        RecurringSchedule locked = schedules.findLockedById(schedule.getId()).orElseThrow();
        if (locked.getStatus() == RecurringScheduleStatus.ENDED) return;
        LocalDate current = locked.getStartDate();
        LocalDate end = locked.getEndDate();
        Set<DayOfWeek> weekdays = locked.getRecurrenceWeekdays();
        Set<LocalDate> existingDates = occurrences.findByScheduleId(locked.getId()).stream()
                .map(Occurrence::getOccurrenceDate).collect(Collectors.toCollection(HashSet::new));

        while (!current.isAfter(end)) {
            if (weekdays.contains(current.getDayOfWeek()) && existingDates.add(current)) {
                Occurrence occurrence = new Occurrence(locked, current);
                occurrence.setQuantity(locked.getDefaultQuantity());
                occurrence.setOrderCloseTime(locked.getDefaultOrderCloseTime());
                occurrence.setReadyByTime(locked.getDefaultReadyByTime());
                occurrences.save(occurrence);
            }
            current = current.plusDays(1);
        }
    }

    /**
     * Updates schedule defaults and reconciles the materialised rows: dates
     * that fell out of the new weekday set are removed (future only, history
     * preserved), newly matching dates are added, and future rows without a
     * per-day override inherit the new defaults. Past occurrences and rows
     * with an override are never touched, so editing defaults cannot rewrite
     * history or clobber a Wednesday exception.
     */
    public RecurringSchedule updateSchedule(Long scheduleId, RecurringScheduleDto dto) {
        RecurringSchedule schedule = schedules.findLockedById(scheduleId).orElseThrow();
        if (schedule.getStatus() == RecurringScheduleStatus.ENDED) {
            throw new IllegalStateException("Ended schedules cannot be updated or reactivated.");
        }
        LocalDate start = dto.getStartDate() != null ? dto.getStartDate() : schedule.getStartDate();
        if (dto.getStartDate() != null && !dto.getStartDate().equals(schedule.getStartDate())
                && dto.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("A replacement schedule start date cannot be in the past.");
        }
        boolean ongoing = dto.getOngoing() != null ? dto.getOngoing() : schedule.isOngoing();
        LocalDate end = ongoing ? (schedule.isOngoing() && start.equals(schedule.getStartDate())
                ? schedule.getEndDate() : start.plusDays(RecurringSchedule.ONGOING_INTERNAL_DAYS_CAP - 1L))
                : (dto.getEndDate() != null ? dto.getEndDate() : schedule.getEndDate());
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Schedule end date cannot be before the start date.");
        }
        Set<DayOfWeek> weekdays = dto.getRecurrenceWeekdays() != null
                ? dto.getRecurrenceWeekdays() : schedule.getRecurrenceWeekdays();
        if (weekdays == null || weekdays.isEmpty()) {
            throw new IllegalArgumentException("recurrence weekdays must contain at least one day");
        }
        ensureMatchingDate(start, end, weekdays);
        Integer qty = Boolean.TRUE.equals(dto.getClearDefaultQuantity())
                ? null : (dto.getDefaultQuantity() != null ? dto.getDefaultQuantity() : schedule.getDefaultQuantity());
        if (qty != null && qty <= 0) {
            throw new IllegalArgumentException("Quantity Available must be at least 1; leave blank for unlimited.");
        }
        String close = dto.getDefaultOrderCloseTime() != null
                ? dto.getDefaultOrderCloseTime() : schedule.getDefaultOrderCloseTime();
        if (close != null && !close.isBlank() && !close.trim().matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException("Orders Close must use 24-hour HH:mm format, e.g. 08:30");
        }
        String ready = dto.getDefaultReadyByTime() != null
                ? dto.getDefaultReadyByTime() : schedule.getDefaultReadyByTime();
        schedule.setStartDate(start);
        schedule.setEndDate(end);
        schedule.setRecurrenceWeekdays(weekdays);
        schedule.setOngoing(ongoing);
        schedule.setDefaultQuantity(qty);
        schedule.setDefaultOrderCloseTime(close);
        schedule.setDefaultReadyByTime(ready);
        RecurringSchedule saved = schedules.save(schedule);

        // Reconcile materialised occurrences against the new range + weekdays.
        LocalDate today = LocalDate.now();
        List<Occurrence> existing = new ArrayList<>(occurrences.findByScheduleId(scheduleId));
        for (Occurrence o : existing) {
            LocalDate date = o.getOccurrenceDate();
            boolean inRange = !date.isBefore(saved.getStartDate()) && !date.isAfter(saved.getEndDate());
            boolean matchesWeekday = saved.getRecurrenceWeekdays().contains(date.getDayOfWeek());
            if (date.isAfter(today)) {
                if (!inRange || !matchesWeekday) {
                    // A date with customer orders is contractual history even when
                    // still in the future. Keep it orderable instead of orphaning
                    // those buyers when the pattern changes.
                    if (!hasCustomerOrders(o)) o.setStatus(OccurrenceStatus.ENDED);
                } else if (o.getStatus() == OccurrenceStatus.ENDED) {
                    o.setStatus(OccurrenceStatus.SCHEDULED);
                }
                occurrences.save(o);
            }
        }
        generateOccurrences(saved);

        List<Occurrence> futureOccurrences = occurrences.findByScheduleId(scheduleId).stream()
                .filter(o -> o.getOccurrenceDate().isAfter(today))
                .collect(Collectors.toList());

        for (Occurrence o : futureOccurrences) {
            OccurrenceOverride override = overrides.findByOccurrenceId(o.getId()).orElse(null);
            Integer proposedQuantity = (override == null || !override.isQuantityOverridden())
                    ? saved.getDefaultQuantity() : o.getQuantity();
            String proposedClose = (override == null || override.getOrderCloseTime() == null)
                    ? saved.getDefaultOrderCloseTime() : o.getOrderCloseTime();
            String proposedReady = (override == null || override.getReadyByTime() == null)
                    ? saved.getDefaultReadyByTime() : o.getReadyByTime();
            assertOrderedOccurrenceEditAllowed(o, proposedQuantity, proposedClose, proposedReady);
            if (override == null || !override.isQuantityOverridden()) {
                o.setQuantity(saved.getDefaultQuantity());
            }
            if (override == null || override.getOrderCloseTime() == null) {
                o.setOrderCloseTime(saved.getDefaultOrderCloseTime());
            }
            if (override == null || override.getReadyByTime() == null) {
                o.setReadyByTime(saved.getDefaultReadyByTime());
            }
            occurrences.save(o);
        }

        return saved;
    }

    public void endSchedule(Long scheduleId) {
        RecurringSchedule schedule = schedules.findLockedById(scheduleId).orElseThrow();
        schedule.setStatus(RecurringScheduleStatus.ENDED);
        schedules.save(schedule);
        // ENDED stops future selling dates while preserving history: past
        // occurrences (and any orders already placed) are never deleted.
        LocalDate today = LocalDate.now();
        for (Occurrence o : occurrences.findByScheduleId(scheduleId)) {
            if (!o.getOccurrenceDate().isBefore(today)) {
                o.setStatus(OccurrenceStatus.ENDED);
                occurrences.save(o);
            }
        }
    }

    /** Extends an active ongoing schedule to a fresh server-owned 90-day horizon. */
    public RecurringSchedule extendOngoingSchedule(Long scheduleId) {
        RecurringSchedule schedule = schedules.findLockedById(scheduleId).orElseThrow();
        if (schedule.getStatus() != RecurringScheduleStatus.ACTIVE) {
            throw new IllegalStateException("Ended schedules cannot be extended.");
        }
        if (!schedule.isOngoing()) {
            throw new IllegalStateException("Only ongoing schedules can be extended.");
        }
        LocalDate target = LocalDate.now().plusDays(RecurringSchedule.ONGOING_INTERNAL_DAYS_CAP - 1L);
        if (target.isAfter(schedule.getEndDate())) {
            schedule.setEndDate(target);
            schedules.save(schedule);
            generateOccurrences(schedule);
        }
        return schedule;
    }

    @Transactional(readOnly = true)
    public RecurringSchedule getSchedule(Long scheduleId) {
        return schedules.findById(scheduleId).orElseThrow();
    }

    /**
     * Ownership-checked schedule load for the HTTP layer. The product/kitchen/
     * seller chain is LAZY, so the check must run inside this transaction —
     * the controller receives a detached entity and can never trigger a
     * LazyInitializationException (which the new HTTP test caught as a 500).
     */
    @Transactional(readOnly = true)
    public RecurringSchedule getOwnedSchedule(Long scheduleId, Long sellerId) {
        RecurringSchedule schedule = schedules.findById(scheduleId).orElseThrow();
        Long ownerId = schedule.getProduct() != null && schedule.getProduct().getKitchen() != null
                && schedule.getProduct().getKitchen().getSeller() != null
                ? schedule.getProduct().getKitchen().getSeller().getId() : null;
        if (ownerId == null || !ownerId.equals(sellerId)) {
            throw new com.example.my_first_spring_api.exception.SellerNotAuthorizedException(
                    "You do not own this schedule");
        }
        return schedule;
    }

    @Transactional(readOnly = true)
    public List<RecurringSchedule> getSellerSchedules(Long sellerId) {
        return schedules.findByProduct_Kitchen_Seller_Id(sellerId);
    }

    /**
     * RECURRING tab cards: one row per schedule owned by this seller, with the
     * product name copied inside the transaction (the association is LAZY).
     */
    @Transactional(readOnly = true)
    public List<ScheduleCardDto> getSellerScheduleCards(Long sellerId) {
        return getSellerSchedules(sellerId).stream()
                .filter(schedule -> schedule.getStatus() == RecurringScheduleStatus.ACTIVE)
                .map(this::toScheduleCard)
                .collect(Collectors.toList());
    }

    /**
     * Single-card read backing PUT /{scheduleId} (Manage Schedule confirmation
     * re-renders from a fresh card rather than trusting the request echo).
     */
    @Transactional(readOnly = true)
    public ScheduleCardDto getScheduleCard(Long scheduleId) {
        return toScheduleCard(schedules.findById(scheduleId).orElseThrow());
    }

    private ScheduleCardDto toScheduleCard(RecurringSchedule schedule) {
        ScheduleCardDto card = new ScheduleCardDto(schedule,
                occurrences.findByScheduleId(schedule.getId()).size());
        if (schedule.getProduct() != null) {
            card.setProductId(schedule.getProduct().getId());
            card.setProductName(schedule.getProduct().getName());
            card.setProductImageUrl(schedule.getProduct().getImageUrl());
        }
        occurrences.findByScheduleIdOrderByOccurrenceDateAsc(schedule.getId()).stream()
                .filter(o -> !o.getOccurrenceDate().isBefore(LocalDate.now()))
                .filter(o -> o.getStatus() != OccurrenceStatus.ENDED)
                .findFirst().ifPresent(next -> {
                    card.setNextOccurrenceStatus(next.getStatus());
                    card.setNextOccurrenceDate(next.getOccurrenceDate());
                });
        return card;
    }

    @Transactional(readOnly = true)
    public Occurrence getOccurrence(Long occurrenceId) {
        return occurrences.findById(occurrenceId).orElseThrow();
    }

    /**
     * Ownership-checked occurrence load for the HTTP layer (same lazy-chain
     * reason as {@link #getOwnedSchedule}: the schedule/product/kitchen/seller
     * graph must be walked inside a transaction).
     */
    @Transactional(readOnly = true)
    public Occurrence getOwnedOccurrence(Long occurrenceId, Long sellerId) {
        Occurrence occurrence = occurrences.findById(occurrenceId).orElseThrow();
        if (occurrence.getSchedule() == null) {
            throw new com.example.my_first_spring_api.exception.SellerNotAuthorizedException(
                    "You do not own this schedule");
        }
        getOwnedSchedule(occurrence.getSchedule().getId(), sellerId);
        return occurrence;
    }

    /**
     * Resolved per-day rows for one schedule, oldest date first. Each row
     * applies its own override (if any) over its own snapshot — sibling dates
     * are never mixed (core domain rule).
     */
    @Transactional(readOnly = true)
    public List<OccurrenceDto> getResolvedOccurrences(Long scheduleId) {
        return occurrences.findByScheduleIdOrderByOccurrenceDateAsc(scheduleId).stream()
                .map(this::toResolvedDto)
                .collect(Collectors.toList());
    }

    /**
     * Same resolution as {@link #getResolvedOccurrence(Long)}, factored so the
     * bulk list can reuse it WITHOUT a self-invocation: a self-call would
     * bypass the Spring proxy and lose the read transaction, detaching every
     * lazy product/kitchen/seller the controller's ownership check must walk
     * (the 403-vs-500 bug the new HTTP test caught).
     */
    private OccurrenceDto toResolvedDto(Occurrence occurrence) {
        OccurrenceOverride override = overrides.findByOccurrenceId(occurrence.getId()).orElse(null);

        Integer qty = (override != null && override.isQuantityOverridden()) ? override.getQuantity() : occurrence.getQuantity();
        String close = (override != null && override.getOrderCloseTime() != null) ? override.getOrderCloseTime() : occurrence.getOrderCloseTime();
        String ready = (override != null && override.getReadyByTime() != null) ? override.getReadyByTime() : occurrence.getReadyByTime();

        OccurrenceDto dto = new OccurrenceDto(occurrence, qty, close, ready);
        if (override != null) {
            if (override.getSoldOut() != null) dto.setSoldOut(override.getSoldOut());
            if (override.getOrdersPaused() != null) dto.setOrdersPaused(override.getOrdersPaused());
        }
        return dto;
    }

    /**
     * Per-day exception for exactly ONE selling date. Only non-null fields are
     * stored, and only this occurrence's row is touched — Monday and Friday
     * keep their own values untouched (core domain rule).
     */
    public Occurrence updateOccurrenceOverride(Long occurrenceId, Integer qty, String closeTime, String readyBy) {
        return updateOccurrenceOverride(occurrenceId, qty, closeTime, readyBy, null, null, null);
    }

    /**
     * Full per-day update backing PATCH /occurrences/{id}: quantity/close/
     * ready overrides plus the sold-out and paused flags. {@code clearQuantity}
     * sets the override quantity to explicit null ("No limit"), because a bare
     * null quantity in a partial PATCH means "leave unchanged".
     */
    public Occurrence updateOccurrenceOverride(Long occurrenceId, Integer qty, String closeTime, String readyBy,
                                               Boolean soldOut, Boolean ordersPaused, Boolean clearQuantity) {
        Occurrence occurrence = occurrences.findByIdForUpdate(occurrenceId).orElseThrow();
        boolean hasChange = qty != null || closeTime != null || readyBy != null || soldOut != null
                || ordersPaused != null || Boolean.TRUE.equals(clearQuantity);
        if (!hasChange) return occurrence;
        Integer proposedQuantity = Boolean.TRUE.equals(clearQuantity) ? null
                : (qty != null ? qty : getResolvedOccurrence(occurrenceId).getQuantity());
        String proposedClose = closeTime != null ? closeTime.trim()
                : getResolvedOccurrence(occurrenceId).getOrderCloseTime();
        String proposedReady = readyBy != null ? readyBy.trim()
                : getResolvedOccurrence(occurrenceId).getReadyByTime();
        assertOrderedOccurrenceEditAllowed(occurrence, proposedQuantity, proposedClose, proposedReady);
        OccurrenceOverride override = overrides.findByOccurrenceId(occurrenceId)
                                               .orElse(new OccurrenceOverride(occurrence));
        if (Boolean.TRUE.equals(clearQuantity)) {
            override.setQuantity(null);
            override.setQuantityOverridden(true);
        } else if (qty != null) {
            if (qty <= 0) {
                throw new IllegalArgumentException("Quantity Available must be at least 1; leave blank for unlimited.");
            }
            override.setQuantity(qty);
        }
        if (closeTime != null) {
            String close = closeTime.trim();
            if (!close.isEmpty() && !close.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                throw new IllegalArgumentException("Orders Close must use 24-hour HH:mm format, e.g. 08:30");
            }
            override.setOrderCloseTime(close.isEmpty() ? null : close);
        }
        if (readyBy != null) {
            String ready = readyBy.trim();
            override.setReadyByTime(ready.isEmpty() ? null : ready);
        }
        if (soldOut != null) override.setSoldOut(soldOut);
        if (ordersPaused != null) override.setOrdersPaused(ordersPaused);
        overrides.save(override);
        // Project the flags onto the occurrence itself so the seller's
        // Sold Out Today / Close Orders Today controls read back instantly.
        if (soldOut != null) occurrence.setSoldOut(soldOut);
        if (ordersPaused != null) occurrence.setOrdersPaused(ordersPaused);
        return occurrences.save(occurrence);
    }

    /** Per-day Sold Out Today that never touches sibling dates. */
    public void markOccurrenceSoldOut(Long occurrenceId, boolean soldOut) {
        updateOccurrenceOverride(occurrenceId, null, null, null, soldOut, null, null);
    }

    /** Per-day Close Orders Today that never touches sibling dates. */
    public void markOccurrencePaused(Long occurrenceId, boolean paused) {
        updateOccurrenceOverride(occurrenceId, null, null, null, null, paused, null);
    }

    /**
     * Resolved read model for one selling date: override fields win when set,
     * otherwise the occurrence's own snapshot applies. Flags resolve with the
     * same precedence (explicit override, else occurrence value).
     */
    @Transactional(readOnly = true)
    public OccurrenceDto getResolvedOccurrence(Long occurrenceId) {
        Occurrence occurrence = occurrences.findById(occurrenceId).orElseThrow();
        OccurrenceOverride override = overrides.findByOccurrenceId(occurrenceId).orElse(null);

        Integer qty = (override != null && override.isQuantityOverridden()) ? override.getQuantity() : occurrence.getQuantity();
        String close = (override != null && override.getOrderCloseTime() != null) ? override.getOrderCloseTime() : occurrence.getOrderCloseTime();
        String ready = (override != null && override.getReadyByTime() != null) ? override.getReadyByTime() : occurrence.getReadyByTime();

        OccurrenceDto dto = new OccurrenceDto(occurrence, qty, close, ready);
        if (override != null) {
            if (override.getSoldOut() != null) dto.setSoldOut(override.getSoldOut());
            if (override.getOrdersPaused() != null) dto.setOrdersPaused(override.getOrdersPaused());
        }
        return dto;
    }

    private void ensureMatchingDate(LocalDate start, LocalDate end, Set<DayOfWeek> weekdays) {
        LocalDate date = start;
        while (!date.isAfter(end)) {
            if (weekdays.contains(date.getDayOfWeek())) return;
            date = date.plusDays(1);
        }
        throw new IllegalArgumentException("Schedule range must contain at least one selected recurrence day.");
    }

    private boolean sameConfiguration(RecurringSchedule schedule, LocalDate start, LocalDate end,
                                      Set<DayOfWeek> weekdays, Integer qty, String close,
                                      String ready, boolean ongoing) {
        return Objects.equals(schedule.getStartDate(), start)
                && Objects.equals(schedule.getEndDate(), end)
                && Objects.equals(schedule.getRecurrenceWeekdays(), weekdays)
                && Objects.equals(schedule.getDefaultQuantity(), qty)
                && Objects.equals(schedule.getDefaultOrderCloseTime(), close)
                && Objects.equals(schedule.getDefaultReadyByTime(), ready)
                && schedule.isOngoing() == ongoing;
    }

    // ==================== BUYER / DASHBOARD OCCURRENCE PROJECTION ====================
    //
    // V2 sections 7/8/14: one product view of the recurring engine. Buyer and
    // seller DTOs never see the schedule engine itself - they see the RESOLVED
    // values of the current/next selling date (override wins), so a Wednesday
    // per-day override is what both sides display and enforce.

    /** ACTIVE schedule for a product, or null (ended / no schedule). */
    @Transactional(readOnly = true)
    public RecurringSchedule findActiveScheduleForProduct(Long productId) {
        if (productId == null) return null;
        RecurringSchedule schedule = schedules.findByProductId(productId).orElse(null);
        if (schedule == null) return null;
        return (schedule.getStatus() == null || schedule.getStatus() == RecurringScheduleStatus.ACTIVE)
                ? schedule : null;
    }

    /**
     * True when the product was ever provisioned with a recurring schedule
     * (any status). Ordering needs BOTH this and a live target occurrence, so
     * an ended/exhausted schedule can never fall back to one-time ordering.
     */
    @Transactional(readOnly = true)
    public boolean hasSchedule(Long productId) {
        return productId != null && schedules.findByProductId(productId).isPresent();
    }

    /** Resolved row for one date of a schedule; null when missing or ENDED. */
    @Transactional(readOnly = true)
    public OccurrenceDto resolveOccurrenceOn(Long scheduleId, LocalDate date) {
        if (scheduleId == null || date == null) return null;
        Occurrence occurrence = occurrences.findByScheduleIdAndOccurrenceDate(scheduleId, date).orElse(null);
        if (occurrence == null || !isOrderableStatus(occurrence.getStatus())) return null;
        return getResolvedOccurrence(occurrence.getId());
    }

    /** First not-ended occurrence on or after {@code from} (null when exhausted). */
    @Transactional(readOnly = true)
    public OccurrenceDto nextOccurrenceFrom(Long scheduleId, LocalDate from) {
        if (scheduleId == null || from == null) return null;
        return occurrences.findByScheduleIdOrderByOccurrenceDateAsc(scheduleId).stream()
                .filter(o -> !o.getOccurrenceDate().isBefore(from))
                .filter(o -> isOrderableStatus(o.getStatus()))
                .findFirst()
                .map(o -> getResolvedOccurrence(o.getId()))
                .orElse(null);
    }

    /**
     * The occurrence a buyer order lands on: today when today is a selling
     * date, otherwise the next future selling date (V2 section 14: "Order
     * Now" today, pre-order the next occurrence otherwise). Null when the
     * product has no active schedule or nothing orderable is left.
     */
    @Transactional(readOnly = true)
    public OccurrenceDto resolveTargetOccurrence(Product product) {
        RecurringSchedule schedule = findActiveScheduleForProduct(product == null ? null : product.getId());
        if (schedule == null) return null;
        LocalDate today = LocalDate.now();
        OccurrenceDto todayOcc = resolveOccurrenceOn(schedule.getId(), today);
        if (isResolvedOrderable(todayOcc, product)) return todayOcc;
        return occurrences.findByScheduleIdOrderByOccurrenceDateAsc(schedule.getId()).stream()
                .filter(o -> o.getOccurrenceDate().isAfter(today))
                .filter(o -> isOrderableStatus(o.getStatus()))
                .map(this::toResolvedDto)
                .filter(o -> isResolvedOrderable(o, product))
                .findFirst().orElse(null);
    }

    /** Resolved row for an explicit date of a product's ACTIVE schedule. */
    @Transactional(readOnly = true)
    public OccurrenceDto resolveOccurrenceForDate(Product product, LocalDate date) {
        RecurringSchedule schedule = findActiveScheduleForProduct(product == null ? null : product.getId());
        if (schedule == null) return null;
        return resolveOccurrenceOn(schedule.getId(), date);
    }

    /** Checkout variant: serializes capacity validation for one occurrence. */
    public OccurrenceDto resolveOccurrenceForDateForUpdate(Product product, LocalDate date) {
        RecurringSchedule schedule = findActiveScheduleForProduct(product == null ? null : product.getId());
        if (schedule == null || date == null) return null;
        Occurrence occurrence = occurrences.findByScheduleIdAndOccurrenceDate(schedule.getId(), date).orElse(null);
        if (occurrence == null) return null;
        occurrence = occurrences.findByIdForUpdate(occurrence.getId()).orElse(null);
        return occurrence == null || !isOrderableStatus(occurrence.getStatus())
                ? null : toResolvedDto(occurrence);
    }

    /**
     * Decorates a ProductDto with the recurring flag plus today's resolved
     * occurrence values (per-day quantity cap, close/ready override, per-day
     * sold-out/paused flags) or, when today is not a selling date, the next
     * occurrence identity/date for the buyer pre-order badge. Called from the
     * buyer-facing and dashboard DTO builders; a no-op for one-time offerings.
     */
    @Transactional(readOnly = true)
    public void decorateProductDto(Product product, com.example.my_first_spring_api.dto.ProductDto dto) {
        if (product == null || product.getId() == null || dto == null) return;
        RecurringSchedule schedule = schedules.findByProductId(product.getId()).orElse(null);
        if (schedule == null) return; // one-time offering: nothing to project
        dto.setRecurring(true);
        if (schedule.getStatus() != null && schedule.getStatus() != RecurringScheduleStatus.ACTIVE) {
            // Ended schedules keep history but nothing is orderable any more
            // (V2 section 12: End Schedule stops future activation only).
            dto.setOrdersClosed(true);
            dto.setLifecycleState("ORDERS_CLOSED");
            return;
        }
        OccurrenceDto target = resolveTargetOccurrence(product);
        if (target != null) {
            dto.setOccurrenceId(target.getId());
            dto.setNextOccurrenceDate(target.getDate());
            applyResolvedOccurrence(dto, target);
        } else {
            dto.setOrdersClosed(true);
            dto.setLifecycleState("ORDERS_CLOSED");
        }
    }

    private void applyResolvedOccurrence(com.example.my_first_spring_api.dto.ProductDto dto, OccurrenceDto occurrence) {
        dto.setMaxQuantity(occurrence.getQuantity());
        int booked = bookedQuantity(occurrence.getId());
        dto.setBookedQuantity(booked);
        dto.setRemainingQuantity(occurrence.getQuantity() == null
                ? null : Math.max(0, occurrence.getQuantity() - booked));
        if (occurrence.getOrderCloseTime() != null) {
            dto.setOrderWindowEnd(occurrence.getOrderCloseTime());
            dto.setCutoffTime(occurrence.getOrderCloseTime());
        }
        if (occurrence.getReadyByTime() != null) dto.setReadyByTime(occurrence.getReadyByTime());
        dto.setSoldOut(Boolean.TRUE.equals(dto.getSoldOut()) || Boolean.TRUE.equals(occurrence.getSoldOut()));
        dto.setOrdersPaused(Boolean.TRUE.equals(dto.getOrdersPaused()) || Boolean.TRUE.equals(occurrence.getOrdersPaused()));
        boolean notOpenYet = occurrence.getDate().isAfter(LocalDate.now())
                && parseTime(dto.getOrderWindowStart()) != null
                && java.time.LocalTime.now().isBefore(parseTime(dto.getOrderWindowStart()));
        boolean closed = Boolean.TRUE.equals(dto.getSoldOut()) || Boolean.TRUE.equals(dto.getOrdersPaused())
                || notOpenYet;
        dto.setOrdersClosed(closed);
        dto.setLifecycleState(closed ? "ORDERS_CLOSED"
                : occurrence.getDate().isAfter(LocalDate.now()) ? "PRE_ORDER" : "LIVE");
    }

    private boolean hasCustomerOrders(Occurrence occurrence) {
        return bookedQuantity(occurrence.getId()) > 0;
    }

    private int bookedQuantity(Long occurrenceId) {
        Occurrence occurrence = occurrences.findById(occurrenceId).orElseThrow();
        Long productId = occurrence.getSchedule().getProduct().getId();
        return orderItems.findByProductId(productId).stream()
                .filter(item -> item.getOrder() != null)
                .filter(item -> item.getOrder().getOrderStatus() != OrderStatus.DRAFT
                        && item.getOrder().getOrderStatus() != OrderStatus.CANCELLED)
                .filter(item -> item.getOccurrence() != null
                        && occurrenceId.equals(item.getOccurrence().getId()))
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum();
    }

    private void assertOrderedOccurrenceEditAllowed(Occurrence occurrence, Integer quantity,
                                                     String close, String ready) {
        int booked = bookedQuantity(occurrence.getId());
        if (booked == 0) return;
        OccurrenceDto current = getResolvedOccurrence(occurrence.getId());
        if (quantity != null && quantity < booked) {
            throw new IllegalStateException("Quantity cannot be lower than the " + booked
                    + " units already ordered for " + occurrence.getOccurrenceDate() + ".");
        }
        if (!Objects.equals(current.getReadyByTime(), ready)) {
            throw new IllegalStateException("Delivery / Ready By cannot change after orders exist for this date.");
        }
        java.time.LocalTime oldClose = parseTime(current.getOrderCloseTime());
        java.time.LocalTime newClose = parseTime(close);
        if (oldClose != null && (newClose == null || newClose.isBefore(oldClose))) {
            throw new IllegalStateException("Orders Close can only be extended after orders exist for this date.");
        }
    }

    private java.time.LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try { return java.time.LocalTime.parse(value.trim()); }
        catch (RuntimeException ignored) { return null; }
    }

    private boolean isOrderableStatus(OccurrenceStatus status) {
        return status == null || status == OccurrenceStatus.SCHEDULED || status == OccurrenceStatus.LIVE;
    }

    private boolean isResolvedOrderable(OccurrenceDto occurrence, Product product) {
        if (occurrence == null || Boolean.TRUE.equals(occurrence.getSoldOut())
                || Boolean.TRUE.equals(occurrence.getOrdersPaused())) return false;
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.LocalTime open = parseTime(product == null ? null : product.getOrderWindowStart());
        if (open != null && now.isBefore(open)) return false;
        if (LocalDate.now().equals(occurrence.getDate())) {
            java.time.LocalTime close = parseTime(occurrence.getOrderCloseTime());
            if (close != null && now.isAfter(close)) return false;
        }
        return true;
    }
}
