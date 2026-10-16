package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.model.PasswordResetRequestStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin review screen API for the manual (Admin-mediated) password recovery flow.
 *
 * <p>Visibility: any Admin (Area or Super) may LIST requests. APPROVE (generate a
 * temporary password) and REJECT are Super Admin only - enforced server-side in
 * {@link PasswordRecoveryService}, never by a client-supplied parameter. The
 * acting reviewer is always resolved from the HTTP session.</p>
 *
 * <p>Spring Security already restricts /api/admin/** to ADMIN and SUPER_ADMIN
 * roles (see SecurityConfig); this controller adds the Super Admin gate for the
 * mutating actions.</p>
 */
@RestController
@RequestMapping("/api/admin/password-recovery")
public class PasswordRecoveryAdminController {

    private final PasswordRecoveryService recoveryService;
    private final BuyerService buyerService;

    @Autowired
    public PasswordRecoveryAdminController(PasswordRecoveryService recoveryService, BuyerService buyerService) {
        this.recoveryService = recoveryService;
        this.buyerService = buyerService;
    }

    /** Request rows (seller name, kitchen, mobile, date, status, reviewer) for the review screen. */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(value = "status", required = false) PasswordResetRequestStatus status,
            HttpSession session) {
        requireAdmin(session);
        return ResponseEntity.ok(recoveryService.listRequests(status));
    }

    /** Generates the one-time temporary password (Super Admin only); plaintext shown exactly once. */
    @PostMapping("/{requestId}/generate-temp-password")
    public ResponseEntity<Map<String, Object>> generateTempPassword(@PathVariable Long requestId,
                                                                    HttpSession session) {
        return ResponseEntity.ok(recoveryService.generateTempPassword(requestId, requireAdmin(session)));
    }

    /** Rejects / closes a request (Super Admin only), optionally with a reason. */
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long requestId,
                                                      @RequestBody(required = false) Map<String, String> body,
                                                      HttpSession session) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(recoveryService.rejectRequest(requestId, requireAdmin(session), reason));
    }

    private User requireAdmin(HttpSession session) {
        return buyerService.requireCurrentBuyer(session);
    }
}
