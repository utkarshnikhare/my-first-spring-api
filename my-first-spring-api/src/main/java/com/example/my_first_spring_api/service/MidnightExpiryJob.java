package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.model.Occurrence;
import com.example.my_first_spring_api.model.OccurrenceStatus;
import com.example.my_first_spring_api.model.RecurringSchedule;
import com.example.my_first_spring_api.model.RecurringScheduleStatus;
import com.example.my_first_spring_api.repository.OccurrenceRepository;
import com.example.my_first_spring_api.repository.RecurringScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Midnight expiry / activation job for SocioMart occurrences.
 *
 * <p>Runs at 00:00 Asia/Kolkata. Performs idempotent, safe updates only:
 * <ul>
 *   <li>Marks occurrences whose service date is in the past as ENDED (so they
 *       disappear from active discovery) while preserving historical rows and
 *       any orders referencing them.</li>
 *   <li>Generates missing occurrences for ACTIVE recurring schedules whose
 *       scheduled weekdays have arrived but no row exists yet.</li>
 * </ul>
 *
 * <p>The scheduler is a convenience refresh, not the sole expiry safeguard.
 * Every buyer API independently rejects past-date occurrences via
 * {@link AvailabilityService}; this job merely keeps stored occurrence rows
 * and derived caches tidy.
 */
@Service
public class MidnightExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(MidnightExpiryJob.class);

    private final OccurrenceRepository occurrenceRepository;
    private final RecurringScheduleRepository scheduleRepository;

    @Autowired
    public MidnightExpiryJob(OccurrenceRepository occurrenceRepository,
                             RecurringScheduleRepository scheduleRepository) {
        this.occurrenceRepository = occurrenceRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Cron: 00:00 Asia/Kolkata every day.
     * Equivalent to "0 0 0 * * ?" in Kolkata time; the JVM default zone is
     * pinned to Asia/Kolkata at startup by {@link AppTimeZone}.
     */
    @Transactional
    public void runForDate(LocalDate date) {
        log.info("Midnight expiry job runForDate running for date {}", date);
        expirePastOccurrences(date);
        activateScheduledOccurrences(date);
    }

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void expireAndActivateOccurrences() {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        log.info("Midnight expiry job running for date {}", today);

        expirePastOccurrences(today);
        activateScheduledOccurrences(today);

        log.info("Midnight expiry job completed for date {}", today);
    }

    /**
     * Mark every occurrence whose date is strictly before today as ENDED.
     * Idempotent: already-ENDED rows are skipped.
     */
    private void expirePastOccurrences(LocalDate today) {
        List<Occurrence> past = occurrenceRepository.findByOccurrenceDateBefore(today);
        int ended = 0;
        for (Occurrence o : past) {
            if (o.getStatus() != OccurrenceStatus.ENDED) {
                o.setStatus(OccurrenceStatus.ENDED);
                o.setUpdatedAt(java.time.LocalDateTime.now());
                ended++;
            }
        }
        if (ended > 0) {
            occurrenceRepository.saveAll(past);
        }
        log.info("Marked {} past occurrences as ENDED", ended);
    }

    /**
     * For every ACTIVE recurring schedule, generate occurrence rows for any
     * scheduled weekdays on or after today that are not already materialised.
     * Idempotent: existing rows are never duplicated or overwritten.
     */
    private void activateScheduledOccurrences(LocalDate today) {
        List<RecurringSchedule> active = scheduleRepository.findByStatus(RecurringScheduleStatus.ACTIVE);
        int generated = 0;
        for (RecurringSchedule schedule : active) {
            LocalDate start = schedule.getStartDate();
            LocalDate end = schedule.getEndDate();
            LocalDate from = today.isAfter(start) ? today : start;
            for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
                if (!schedule.getRecurrenceWeekdays().contains(d.getDayOfWeek())) continue;
                boolean exists = occurrenceRepository
                        .existsByScheduleIdAndOccurrenceDate(schedule.getId(), d);
                if (!exists) {
                    Occurrence o = new Occurrence(schedule, d);
                    o.setQuantity(schedule.getDefaultQuantity());
                    o.setOrderCloseTime(schedule.getDefaultOrderCloseTime());
                    o.setReadyByTime(schedule.getDefaultReadyByTime());
                    o.setStatus(OccurrenceStatus.SCHEDULED);
                    occurrenceRepository.save(o);
                    generated++;
                }
            }
        }
        log.info("Generated {} missing occurrences for active schedules", generated);
    }
}
