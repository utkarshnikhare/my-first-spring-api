package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.OccurrenceDto;
import com.example.my_first_spring_api.dto.RecurringScheduleDto;
import com.example.my_first_spring_api.dto.ScheduleCardDto;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.Occurrence;
import com.example.my_first_spring_api.model.RecurringSchedule;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.RecurringScheduleService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seller/schedules")
public class RecurringScheduleController {

    private final RecurringScheduleService scheduleService;
    private final BuyerService authService;

    @Autowired
    public RecurringScheduleController(RecurringScheduleService scheduleService,
                                       BuyerService authService) {
        this.scheduleService = scheduleService;
        this.authService = authService;
    }

    private User requireSeller(HttpSession session) {
        User user = authService.getCurrentBuyer(session);
        if (user == null) throw new BuyerNotAuthenticatedException("Authentication required. Please log in.");
        if (user.getRole() != UserRole.SELLER) throw new SellerNotAuthorizedException("Only sellers can perform this action");
        return user;
    }

    private void requireOwnedSchedule(RecurringSchedule schedule, User seller) {
        Long ownerId = schedule.getProduct() != null && schedule.getProduct().getKitchen() != null
                && schedule.getProduct().getKitchen().getSeller() != null
                ? schedule.getProduct().getKitchen().getSeller().getId() : null;
        if (ownerId == null || !ownerId.equals(seller.getId())) {
            throw new SellerNotAuthorizedException("You do not own this schedule");
        }
    }

    private void requireOwnedOccurrence(Occurrence occurrence, User seller) {
        if (occurrence.getSchedule() == null) {
            throw new SellerNotAuthorizedException("You do not own this schedule");
        }
        requireOwnedSchedule(occurrence.getSchedule(), seller);
    }

    /**
     * Seller's recurring cards for the RECURRING tab. Ownership is enforced by
     * the service query (seller id), so one seller can never list another's.
     */
    @GetMapping
    public ResponseEntity<List<ScheduleCardDto>> listSchedules(HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(scheduleService.getSellerScheduleCards(seller.getId()));
    }

    /** Resolved per-day rows for one schedule (RECURRING tab detail). */
    @GetMapping("/{scheduleId}/occurrences")
    public ResponseEntity<List<OccurrenceDto>> listOccurrences(@PathVariable Long scheduleId,
                                                               HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedSchedule(scheduleId, seller.getId());
        return ResponseEntity.ok(scheduleService.getResolvedOccurrences(scheduleId));
    }

    /** One ownership-checked schedule card for Manage Schedule. */
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ScheduleCardDto> getSchedule(@PathVariable Long scheduleId,
                                                       HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedSchedule(scheduleId, seller.getId());
        return ResponseEntity.ok(scheduleService.getScheduleCard(scheduleId));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<RecurringSchedule> createSchedule(@PathVariable Long productId,
                                                           @RequestBody RecurringScheduleDto dto,
                                                           HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(scheduleService.createOwnedSchedule(productId, dto, seller.getId()));
    }

    @PostMapping("/{scheduleId}/end")
    public ResponseEntity<Void> endSchedule(@PathVariable Long scheduleId, HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedSchedule(scheduleId, seller.getId());

        scheduleService.endSchedule(scheduleId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{scheduleId}/extend")
    public ResponseEntity<ScheduleCardDto> extendSchedule(@PathVariable Long scheduleId, HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedSchedule(scheduleId, seller.getId());
        scheduleService.extendOngoingSchedule(scheduleId);
        return ResponseEntity.ok(scheduleService.getScheduleCard(scheduleId));
    }

    /**
     * Manage Schedule: edit the repeating rule / future defaults for ONE
     * schedule. Past occurrences and per-day overrides are preserved by the
     * service; only future un-overridden rows inherit the new defaults.
     */
    @PutMapping("/{scheduleId}")
    public ResponseEntity<ScheduleCardDto> updateSchedule(@PathVariable Long scheduleId,
                                                          @RequestBody RecurringScheduleDto dto,
                                                          HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedSchedule(scheduleId, seller.getId());
        scheduleService.updateSchedule(scheduleId, dto);
        return ResponseEntity.ok(scheduleService.getScheduleCard(scheduleId));
    }

    @GetMapping("/occurrences/{occurrenceId}")
    public ResponseEntity<OccurrenceDto> getResolvedOccurrence(@PathVariable Long occurrenceId, HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedOccurrence(occurrenceId, seller.getId());
        return ResponseEntity.ok(scheduleService.getResolvedOccurrence(occurrenceId));
    }

    /**
     * Per-day override for exactly one selling date. Only the supplied fields
     * change; sibling dates are never touched (core domain rule).
     */
    @PatchMapping("/occurrences/{occurrenceId}")
    public ResponseEntity<OccurrenceDto> updateOccurrence(@PathVariable Long occurrenceId,
                                                      @RequestBody OccurrenceDto dto,
                                                      HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedOccurrence(occurrenceId, seller.getId());
        scheduleService.updateOccurrenceOverride(occurrenceId, dto.getQuantity(),
                dto.getOrderCloseTime(), dto.getReadyByTime(),
                dto.getSoldOut(), dto.getOrdersPaused(), dto.getClearQuantity());
        return ResponseEntity.ok(scheduleService.getResolvedOccurrence(occurrenceId));
    }

    /** Per-day Sold Out Today toggle (this date only). */
    @PostMapping("/occurrences/{occurrenceId}/sold-out")
    public ResponseEntity<OccurrenceDto> markOccurrenceSoldOut(@PathVariable Long occurrenceId,
                                                               @RequestBody(required = false) java.util.Map<String, Object> body,
                                                               HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedOccurrence(occurrenceId, seller.getId());
        Object raw = body == null ? null : body.get("soldOut");
        boolean soldOut = !(raw instanceof Boolean) || (Boolean) raw;
        scheduleService.markOccurrenceSoldOut(occurrenceId, soldOut);
        return ResponseEntity.ok(scheduleService.getResolvedOccurrence(occurrenceId));
    }

    /** Per-day Close Orders Today toggle (this date only). */
    @PostMapping("/occurrences/{occurrenceId}/pause")
    public ResponseEntity<OccurrenceDto> markOccurrencePaused(@PathVariable Long occurrenceId,
                                                              @RequestBody(required = false) java.util.Map<String, Object> body,
                                                              HttpSession session) {
        User seller = requireSeller(session);
        scheduleService.getOwnedOccurrence(occurrenceId, seller.getId());
        Object raw = body == null ? null : body.get("paused");
        boolean paused = !(raw instanceof Boolean) || (Boolean) raw;
        scheduleService.markOccurrencePaused(occurrenceId, paused);
        return ResponseEntity.ok(scheduleService.getResolvedOccurrence(occurrenceId));
    }
}
