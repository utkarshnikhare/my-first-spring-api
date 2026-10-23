package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.AuthResponseDto;
import com.example.my_first_spring_api.dto.BuyerRegistrationRequestDto;
import com.example.my_first_spring_api.dto.ChangePasswordRequestDto;
import com.example.my_first_spring_api.dto.CredentialLoginRequestDto;
import com.example.my_first_spring_api.dto.ForgotPasswordRequestDto;
import com.example.my_first_spring_api.dto.ResetPasswordRequestDto;
import com.example.my_first_spring_api.dto.SellerRegistrationRequestDto;
import com.example.my_first_spring_api.exception.InvalidCredentialsException;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.security.AuthRateLimiter;
import com.example.my_first_spring_api.service.AdminAuditService;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.AuthService;
import com.example.my_first_spring_api.service.AuthService.PasswordResetResponseDto;
import com.example.my_first_spring_api.service.OrderService;
import com.example.my_first_spring_api.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final BuyerService buyerService;
    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    /**
     * Login/registration rate limiter. Optional field-injection so the hand-constructed
     * {@code AuthControllerProfileStateTest} (3-arg constructor) keeps working; all call
     * sites are null-guarded.
     */
    @Autowired(required = false)
    private AuthRateLimiter rateLimiter;
    /** Append-only audit for login lockouts. Optional for the same reason as above. */
    @Autowired(required = false)
    private AdminAuditService auditService;
    /**
     * Manual (Admin-mediated) password recovery. Optional field-injection for the
     * same hand-constructed-test reason; call sites are null-guarded.
     */
    @Autowired(required = false)
    private PasswordRecoveryService passwordRecovery;

    @Autowired
    public AuthController(BuyerService buyerService, AuthService authService,
                          SecurityContextRepository securityContextRepository) {
        this.buyerService = buyerService;
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/config")
    public Map<String, Boolean> config() {
        return Map.of(
                "directAuthEnabled", authService.isDirectAuthEnabled(),
                "demoRegistrationEnabled", authService.isDirectAuthEnabled(),
                "adminLoginConfigured", authService.isAdminPasswordConfigured(),
                "superAdminLoginConfigured", authService.isSuperAdminPasswordConfigured());
    }

    @GetMapping("/registration-options")
    public ResponseEntity<?> registrationOptions() {
        return ResponseEntity.ok(authService.registrationOptions());
    }

    @PostMapping("/register/buyer")
    public ResponseEntity<AuthResponseDto> registerBuyer(@Valid @RequestBody BuyerRegistrationRequestDto request,
                                                           HttpServletRequest servletRequest,
                                                           HttpServletResponse response) {
        String ip = clientIp(servletRequest);
        if (rateLimiter != null) {
            rateLimiter.assertRegistrationAllowed(ip);
            rateLimiter.recordRegistration(ip);
        }
        User buyer = authService.registerBuyer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticate(buyer, "Registration successful",
                servletRequest, response));
    }

    @PostMapping("/register/seller")
    public ResponseEntity<AuthResponseDto> registerSeller(@Valid @RequestBody SellerRegistrationRequestDto request,
                                                            HttpServletRequest servletRequest,
                                                            HttpServletResponse response) {
        String ip = clientIp(servletRequest);
        if (rateLimiter != null) {
            rateLimiter.assertRegistrationAllowed(ip);
            rateLimiter.recordRegistration(ip);
        }
        AuthService.SellerRegistration registration = authService.registerSeller(request);
        AuthResponseDto result = authenticate(registration.user(), "Registration submitted for Admin approval",
                servletRequest, response);
        result.setKitchenSlug(registration.kitchenSlug());
        result.setKitchenUrl("/kitchen/" + registration.kitchenSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping(value = {"/login", "/admin", "/admin/login"})
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody CredentialLoginRequestDto request,
                                                  HttpServletRequest servletRequest,
                                                  HttpServletResponse response) {
        String mobile = request.getMobileNumber();
        String ip = clientIp(servletRequest);
        String uri = servletRequest != null ? servletRequest.getRequestURI() : null;
        boolean isAdmin = (uri != null && uri.contains("/admin"))
                || (rateLimiter != null && rateLimiter.isAdminMobile(mobile));
        if (rateLimiter != null) {
            // Rejects with 429 when this mobile or IP is currently locked out.
            rateLimiter.assertLoginAllowed(mobile, ip);
        }
        try {
            AuthService.LoginResult outcome = authService.loginWithOutcome(request);
            User user = outcome.user();
            if (rateLimiter != null) {
                rateLimiter.clearLoginFailures(mobile);
            }
            boolean tempPasswordLogin = outcome.tempPasswordLogin();
            return ResponseEntity.ok(authenticate(user, tempPasswordLogin
                    ? "Logged in with your temporary password. You must set a new password to continue."
                    : "Logged in successfully", servletRequest, response, tempPasswordLogin));
        } catch (InvalidCredentialsException ex) {
            if (rateLimiter != null && rateLimiter.recordLoginFailure(mobile, ip, isAdmin)) {
                auditLoginLockout(mobile, ip);
            }
            throw ex;
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request,
                                             HttpServletRequest servletRequest) {
        if (StringUtils.hasText(request.getMobileNumber())) {
            // Manual (Admin-mediated) recovery V1: always the same neutral answer, and a
            // PENDING row in the Super Admin review queue. Buyers and unknown mobiles
            // get the identical answer and create nothing - no account probing.
            String mobile = request.getMobileNumber().trim();
            String ip = clientIp(servletRequest);
            if (rateLimiter != null) {
                rateLimiter.assertRecoveryAllowed(mobile, ip);
                rateLimiter.recordRecovery(mobile, ip);
            }
            if (passwordRecovery != null) {
                passwordRecovery.requestRecoveryByMobile(mobile);
            }
            return ResponseEntity.ok(Map.of("message",
                    PasswordRecoveryService.NEUTRAL_REQUEST_MESSAGE));
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Provide the e-mail or the mobile number registered with your account.");
        }
        PasswordResetResponseDto response = authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * Forced (or voluntary) password change. Verifies the current password - the
     * original one or the temporary password that opened this session - writes the
     * new BCrypt hash (>= 10 characters, confirmation must match) and lifts the
     * forced-change session flag.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDto request, HttpSession session) {
        User user = buyerService.requireCurrentBuyer(session);
        authService.changePassword(user, request.getCurrentPassword(), request.getNewPassword(),
                request.getConfirmPassword());
        session.removeAttribute(PasswordRecoveryService.MUST_CHANGE_PASSWORD_SESSION_ATTR);
        return ResponseEntity.ok(Map.of("message",
                "Password changed successfully. You can now continue normally."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message",
                "Password has been reset. You can now log in with your new password."));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDto> me(HttpServletRequest request) {
        User user = buyerService.getCurrentBuyer(request.getSession(false));
        if (user == null) {
            return ResponseEntity.ok(new AuthResponseDto(false, "Not authenticated", null, null, null, null, null));
        }
        if (user.isBlocked()) {
            // The blocked session keeps /api/auth/** reachable (see
            // UserSessionAuthorizationFilter.isAuthEscapeHatch) precisely so the
            // client can read this state and log out; no profile data is exposed.
            return ResponseEntity.ok(new AuthResponseDto(false, "Your account is blocked", null, null, null, null, null));
        }
        return ResponseEntity.ok(toAuthResponse(user, "Authenticated"));
    }

    @PostMapping("/become-seller")
    public ResponseEntity<Void> becomeSeller() {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Complete seller registration to submit kitchen details and create a pending storefront.");
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            buyerService.logout(session);
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private AuthResponseDto authenticate(User user, String message, HttpServletRequest request,
                                         HttpServletResponse response) {
        return authenticate(user, message, request, response, false);
    }

    private AuthResponseDto authenticate(User user, String message, HttpServletRequest request,
                                         HttpServletResponse response, boolean mustChangePassword) {
        HttpSession session = request.getSession(true);
        if (!java.util.Objects.equals(session.getAttribute(BuyerService.BUYER_SESSION_KEY), user.getId())) {
            session.removeAttribute(OrderService.DRAFT_ORDER_SESSION_KEY);
        }
        request.changeSessionId();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, user.getId());
        // Manual recovery: every fresh login starts from a clean slate; only a
        // temporary-password login carries the forced-change flag into the session
        // (JDBC-backed, so it survives a restart). The interceptor reads this flag.
        session.removeAttribute(PasswordRecoveryService.MUST_CHANGE_PASSWORD_SESSION_ATTR);
        if (mustChangePassword) {
            session.setAttribute(PasswordRecoveryService.MUST_CHANGE_PASSWORD_SESSION_ATTR, Boolean.TRUE);
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        AuthResponseDto dto = toAuthResponse(user, message);
        dto.setMustChangePassword(mustChangePassword);
        return dto;
    }

    private static AuthResponseDto toAuthResponse(User user, String message) {
        AuthResponseDto dto = new AuthResponseDto(true, message, user.getId(), user.getName(),
                user.getMobileNumber(), user.getFlatHouseNumber(), user.getRole().name(),
                user.getSellerApprovalStatus(), user.getSociety(), user.getBuilding());
        dto.setArea(user.getArea());
        dto.setSellerStatusReason(user.getSellerStatusReason());
        return dto;
    }

    /**
     * Resolves the client IP for rate limiting. Prefers the first entry of
     * {@code X-Forwarded-For} (set by the Render proxy) and falls back to the socket
     * address. Only the left-most entry is trusted, as it is the one appended by our
     * own proxy; entries further right are client-controlled and spoofable.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Appends a LOGIN_LOCKED_OUT audit row when the failure threshold trips. actor is
     * SYSTEM (no authenticated user); the mobile is captured as the target label so an
     * Admin can correlate lockouts with an account. Failures are swallowed by
     * {@link AdminAuditService#record} itself, so this can never turn a 401 into a 500.
     */
    private void auditLoginLockout(String mobile, String ip) {
        if (auditService == null) {
            return;
        }
        auditService.record(AdminAuditService.LOGIN_LOCKED_OUT, null, "USER", null, mobile,
                null, "LOCKED", "5 failed login attempts within 15 minutes from IP " + ip);
    }
}
