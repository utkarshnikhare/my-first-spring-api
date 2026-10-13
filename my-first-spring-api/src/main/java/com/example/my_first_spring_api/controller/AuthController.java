package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.AuthResponseDto;
import com.example.my_first_spring_api.dto.BuyerRegistrationRequestDto;
import com.example.my_first_spring_api.dto.CredentialLoginRequestDto;
import com.example.my_first_spring_api.dto.SellerRegistrationRequestDto;
import com.example.my_first_spring_api.exception.InvalidCredentialsException;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.security.AuthRateLimiter;
import com.example.my_first_spring_api.service.AdminAuditService;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.AuthService;
import com.example.my_first_spring_api.service.OrderService;
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

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody CredentialLoginRequestDto request,
                                                  HttpServletRequest servletRequest,
                                                  HttpServletResponse response) {
        String mobile = request.getMobileNumber();
        String ip = clientIp(servletRequest);
        if (rateLimiter != null) {
            // Rejects with 429 when this mobile or IP is currently locked out.
            rateLimiter.assertLoginAllowed(mobile, ip);
        }
        try {
            User user = authService.login(request);
            if (rateLimiter != null) {
                rateLimiter.clearLoginFailures(mobile);
            }
            return ResponseEntity.ok(authenticate(user, "Logged in successfully", servletRequest, response));
        } catch (InvalidCredentialsException ex) {
            if (rateLimiter != null && rateLimiter.recordLoginFailure(mobile, ip)) {
                auditLoginLockout(mobile, ip);
            }
            throw ex;
        }
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
        HttpSession session = request.getSession(true);
        if (!java.util.Objects.equals(session.getAttribute(BuyerService.BUYER_SESSION_KEY), user.getId())) {
            session.removeAttribute(OrderService.DRAFT_ORDER_SESSION_KEY);
        }
        request.changeSessionId();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, user.getId());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return toAuthResponse(user, message);
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
