package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.PasswordResetRequest;
import com.example.my_first_spring_api.model.PasswordResetRequestStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.PasswordResetRequestRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manual (Admin-mediated) password recovery, V1.
 *
 * <p>Sellers and admins who forget their password request a reset by mobile
 * number (neutral answer, no account probing); a Super Admin reviews the request
 * and generates a one-time temporary password (shown exactly once, BCrypt-hashed
 * at rest, valid 24 hours or until first use); the user is forced to pick a new
 * password on first login.</p>
 *
 * <p>Hierarchy: Area Admins may VIEW requests, only Super Admins may approve or
 * reject them; a Super Admin may reset another Super Admin's password through the
 * same reviewed process. Buyers are strictly excluded - they keep the mobile-OTP
 * flow and never appear in this table.</p>
 */
@Service
public class PasswordRecoveryService {

    /** Session flag set when the user authenticated with a temporary password. */
    public static final String MUST_CHANGE_PASSWORD_SESSION_ATTR = "MUST_CHANGE_PASSWORD";

    /**
     * Neutral answer for the forgot-password request. Identical whether the mobile
     * exists or not, so the endpoint cannot be used to probe registered numbers.
     */
    public static final String NEUTRAL_REQUEST_MESSAGE =
            "If an account matches those details, a password reset request has been created "
                    + "and will be reviewed by an administrator.";

    /** Temporary passwords live 24 hours from generation, or until first use. */
    private static final Duration TEMP_PASSWORD_TTL = Duration.ofHours(24);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordRecoveryService.class);

    private final PasswordResetRequestRepository requests;
    private final UserRepository users;
    private final KitchenRepository kitchens;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService auditService;
    private final AnalyticsService analyticsService;

    public PasswordRecoveryService(PasswordResetRequestRepository requests, UserRepository users,
                                   KitchenRepository kitchens, PasswordEncoder passwordEncoder,
                                   AdminAuditService auditService, AnalyticsService analyticsService) {
        this.requests = requests;
        this.users = users;
        this.kitchens = kitchens;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.analyticsService = analyticsService;
    }

    // ---- Request (seller/admin self-service) --------------------------------

    /**
     * Creates a PENDING reset request for the given mobile. Returns {@code false}
     * silently for unknown mobiles and buyers: the HTTP answer is neutral either
     * way, so nothing about account existence leaks.
     */
    @Transactional
    public boolean requestRecoveryByMobile(String mobile) {
        User user = users.findByMobileNumber(mobile).orElse(null);
        if (user == null || user.getRole() == UserRole.BUYER) {
            return false;
        }
        PasswordResetRequest request = new PasswordResetRequest(user);
        request = requests.save(request);
        audit(AdminAuditService.PASSWORD_RECOVERY_REQUESTED, user, "PASSWORD_RESET_REQUEST",
                request.getId(), user.getMobileNumber(), null, PasswordResetRequestStatus.PENDING.name(),
                "Self-service password recovery requested by the account owner");
        return true;
    }

    // ---- Review (Super Admin only) -----------------------------------------

    /** Request rows for the Admin review screen, newest first. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRequests(PasswordResetRequestStatus status) {
        List<PasswordResetRequest> rows = status == null
                ? requests.findAllByOrderByCreatedAtDesc()
                : requests.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(this::toRow).collect(Collectors.toList());
    }

    /**
     * Generates the one-time temporary password for a PENDING request. The
     * plaintext is returned exactly once (for manual sharing over WhatsApp); only
     * its BCrypt hash is persisted.
     */
    @Transactional
    public Map<String, Object> generateTempPassword(Long requestId, User actingUser) {
        requireSuperAdmin(actingUser);
        PasswordResetRequest request = requireRequest(requestId);
        if (request.getStatus() != PasswordResetRequestStatus.PENDING) {
            throw new IllegalStateException("This reset request has already been "
                    + request.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        String tempPassword = generateTempPasswordValue();
        request.setTempPasswordHash(passwordEncoder.encode(tempPassword));
        request.setTempPasswordUsed(false);
        request.setTempPasswordExpiresAt(LocalDateTime.now().plus(TEMP_PASSWORD_TTL));
        request.setStatus(PasswordResetRequestStatus.APPROVED);
        request.setReviewer(actingUser);
        request.setActionTime(LocalDateTime.now());
        requests.save(request);
        audit(AdminAuditService.PASSWORD_RECOVERY_APPROVED, actingUser, "PASSWORD_RESET_REQUEST",
                request.getId(), request.getUser().getMobileNumber(),
                PasswordResetRequestStatus.PENDING.name(), PasswordResetRequestStatus.APPROVED.name(),
                "Temporary password generated; valid 24 hours or until first use");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", request.getId());
        out.put("mobile", request.getUser().getMobileNumber());
        out.put("userName", request.getUser().getName());
        out.put("tempPassword", tempPassword);
        out.put("expiresAt", request.getTempPasswordExpiresAt().toString());
        out.put("warning", "This temporary password is displayed only once. Share it with the "
                + "user over a trusted channel; they must change it at first login.");
        return out;
    }

    // ---- Review action (Super Admin gate) ----------------------------------

    /** Rejects / closes a PENDING request. Super Admin only. */
    @Transactional
    public Map<String, Object> rejectRequest(Long requestId, User actingUser, String reason) {
        requireSuperAdmin(actingUser);
        PasswordResetRequest request = requireRequest(requestId);
        if (request.getStatus() != PasswordResetRequestStatus.PENDING) {
            throw new IllegalStateException("This reset request has already been "
                    + request.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        request.setStatus(PasswordResetRequestStatus.REJECTED);
        request.setReviewer(actingUser);
        request.setActionTime(LocalDateTime.now());
        String trimmedReason = StringUtils.hasText(reason) ? reason.trim() : null;
        if (trimmedReason != null && trimmedReason.length() > 500) {
            trimmedReason = trimmedReason.substring(0, 500);
        }
        request.setRejectionReason(trimmedReason);
        requests.save(request);
        audit(AdminAuditService.PASSWORD_RECOVERY_REJECTED, actingUser, "PASSWORD_RESET_REQUEST",
                request.getId(), request.getUser().getMobileNumber(),
                PasswordResetRequestStatus.PENDING.name(), PasswordResetRequestStatus.REJECTED.name(),
                trimmedReason);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", request.getId());
        out.put("status", request.getStatus().name());
        out.put("actionTime", request.getActionTime().toString());
        out.put("rejectionReason", trimmedReason);
        return out;
    }

    // ---- Login / change-password integration --------------------------------

    /**
     * Called by {@code AuthService} when the standard password check failed.
     * Returns {@code true} (and consumes the request - "first use") when the raw
     * password matches an APPROVED, unused, unexpired temporary password for this
     * user. Expired or already-used temporary passwords never match, so they are
     * dead the moment they lapse.
     */
    @Transactional
    public boolean consumeTempPasswordOnLogin(User user, String rawPassword) {
        if (user.getRole() == UserRole.BUYER || !StringUtils.hasText(rawPassword)) {
            return false;
        }
        for (PasswordResetRequest request : requests.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (request.getStatus() != PasswordResetRequestStatus.APPROVED
                    || request.isTempPasswordUsed()
                    || request.isTempPasswordExpired()
                    || request.getTempPasswordHash() == null) {
                continue;
            }
            if (passwordEncoder.matches(rawPassword, request.getTempPasswordHash())) {
                request.setTempPasswordUsed(true);
                requests.save(request);
                audit(AdminAuditService.PASSWORD_TEMP_USED, user, "PASSWORD_RESET_REQUEST",
                        request.getId(), user.getMobileNumber(),
                        PasswordResetRequestStatus.APPROVED.name(), "USED",
                        "First login with the temporary password; forced password change required");
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies a raw password against ANY approved temporary password hash of this
     * user (used or not). Used by the forced change-password step so the user can
     * confirm with the temporary password that opened this session even though it
     * was already consumed at login.
     */
    @Transactional(readOnly = true)
    public boolean verifyTempPasswordFor(User user, String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            return false;
        }
        return requests.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(r -> r.getStatus() == PasswordResetRequestStatus.APPROVED
                        && r.getTempPasswordHash() != null)
                .anyMatch(r -> passwordEncoder.matches(rawPassword, r.getTempPasswordHash()));
    }

    // ---- Internal helpers ----------------------------------------------------

    private static void requireSuperAdmin(User actingUser) {
        if (actingUser == null || actingUser.getRole() != UserRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a Super Admin can review password reset requests.");
        }
    }

    private PasswordResetRequest requireRequest(Long requestId) {
        return requests.findById(requestId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Password reset request not found."));
    }

    /** 16 URL-safe characters from 12 CSPRNG bytes - well above the 10-char policy. */
    private static String generateTempPasswordValue() {
        byte[] bytes = new byte[12];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Map<String, Object> toRow(PasswordResetRequest request) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("requestId", request.getId());
        row.put("userName", request.getUser().getName());
        row.put("mobile", request.getUser().getMobileNumber());
        row.put("role", request.getUser().getRole().name());
        row.put("kitchen", kitchenLabel(request.getUser()));
        row.put("requestedAt", request.getCreatedAt().toString());
        row.put("status", request.getStatus().name());
        row.put("reviewer", request.getReviewer() != null ? request.getReviewer().getName() : null);
        row.put("actionTime", request.getActionTime() != null ? request.getActionTime().toString() : null);
        row.put("tempPasswordUsed", request.isTempPasswordUsed());
        row.put("tempPasswordExpiresAt", request.getTempPasswordExpiresAt() != null
                ? request.getTempPasswordExpiresAt().toString() : null);
        row.put("rejectionReason", request.getRejectionReason());
        return row;
    }

    /** Kitchen display names for the review screen; null for non-sellers. */
    private String kitchenLabel(User user) {
        if (user.getRole() != UserRole.SELLER) {
            return null;
        }
        return kitchens.findBySeller(user).stream()
                .map(k -> k.getDisplayName() != null ? k.getDisplayName() : k.getName())
                .collect(Collectors.joining(", "));
    }

    private void audit(String action, User actor, String targetType, Long targetId,
                       String targetLabel, String oldState, String newState, String reason) {
        if (auditService != null) {
            auditService.record(action, actor, targetType, targetId, targetLabel, oldState, newState, reason);
        }
    }

    void recordPasswordChangedAnalytics(User user) {
        if (analyticsService != null) {
            analyticsService.record(AnalyticsService.EV_USER_PASSWORD_RESET,
                    user.getId(), user.getMobileNumber(), null, user.getName());
        }
        LOGGER.info("Password changed for user id={} via the manual recovery flow", user.getId());
    }
}
