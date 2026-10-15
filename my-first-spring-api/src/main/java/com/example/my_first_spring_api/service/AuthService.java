package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AuthSettings;
import com.example.my_first_spring_api.dto.BuyerRegistrationRequestDto;
import com.example.my_first_spring_api.dto.CredentialLoginRequestDto;
import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.dto.ForgotPasswordRequestDto;
import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.ResetPasswordRequestDto;
import com.example.my_first_spring_api.dto.SellerRegistrationRequestDto;
import com.example.my_first_spring_api.exception.AccountAlreadyExistsException;
import com.example.my_first_spring_api.exception.InvalidCredentialsException;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.PasswordResetToken;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.PasswordResetTokenRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

@Service
public class AuthService {
    /** One-time bearer tokens for the forgotten-password flow. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 1 hour TTL for a reset token. */
    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final KitchenRepository kitchens;
    private final LocationService locations;
    private final SellerService sellerService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final AnalyticsService analyticsService;
    private final PasswordResetTokenRepository passwordResetTokens;
    /** Append-only audit for the slug reservation. Field-injected (optional) so manual test construction keeps working. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AdminAuditService auditService;

    public AuthService(UserRepository users, KitchenRepository kitchens, LocationService locations,
                       SellerService sellerService, PasswordEncoder passwordEncoder, Environment environment,
                       AnalyticsService analyticsService, PasswordResetTokenRepository passwordResetTokens) {
        this.users = users;
        this.kitchens = kitchens;
        this.locations = locations;
        this.sellerService = sellerService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.analyticsService = analyticsService;
        this.passwordResetTokens = passwordResetTokens;
    }

    public record SellerRegistration(User user, String kitchenSlug) {}

    @Transactional
    public User registerBuyer(@Valid BuyerRegistrationRequestDto request) {
        requireDirectAuthEnabled();
        ensureMobileAvailable(request.getMobileNumber());
        requireBcryptCompatible(request.getPassword());
        User buyer = new User(request.getName().trim(), request.getMobileNumber(), clean(request.getFlatHouseNumber()),
                UserRole.BUYER);
        buyer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        buyer.setBuilding(clean(request.getBuilding()));
        applyBuyerLocation(buyer, request.getAreaId(), request.getSocietyId());
        User saved = saveNewUser(buyer);
        analyticsService.record(AnalyticsService.EV_USER_REGISTERED, saved.getId(), saved.getMobileNumber(),
                null, saved.getName());
        return saved;
    }

    @Transactional
    public SellerRegistration registerSeller(@Valid SellerRegistrationRequestDto request) {
        requireDirectAuthEnabled();
        ensureMobileAvailable(request.getMobileNumber());
        requireBcryptCompatible(request.getPassword());
        Area primaryArea = requireActiveArea(request.getPrimaryAreaId());
        Society primarySociety = requireActiveSociety(request.getPrimarySocietyId(), primaryArea);
        Area serviceArea = requireActiveArea(request.getServiceAreaId());
        // The public URL is generated and reserved server-side (never trusted from
        // the browser): the seller's requested slug is normalised, then reserved
        // against the DB unique constraint inside createKitchen, which appends
        // -2/-3 when the exact slug is already taken by another PENDING/APPROVED
        // application. A duplicate is therefore impossible even under a race.
        String desiredSlug = SlugService.slugify(request.getKitchenSlug());

        User seller = new User(request.getSellerName().trim(), request.getMobileNumber(), null, UserRole.SELLER);
        seller.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        seller.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        seller.setSellerWhatsappNumber(request.getWhatsappNumber());
        seller.setSellerAlternateContact(clean(request.getAlternateContact()));
        String sellerCategory = request.getSellerCategory().trim().toUpperCase(Locale.ROOT);
        seller.setSellerCategory(sellerCategory);
        seller.setSociety(primarySociety.getName());
        seller.setArea(primaryArea.getName());
        seller.setSocietyRef(primarySociety);
        seller.setAreaRef(primaryArea);
        seller.setBuilding(clean(request.getBuilding()));
        saveNewUser(seller);

        KitchenCreateDto kitchen = new KitchenCreateDto();
        kitchen.setName(desiredSlug);
        kitchen.setDisplayName(request.getKitchenName().trim());
        kitchen.setShortDescription(clean(request.getShortDescription()));
        kitchen.setDescription(clean(request.getShortDescription()));
        kitchen.setSpeciality(request.getSpeciality().trim());
        kitchen.setSociety(primarySociety.getName());
        kitchen.setBuilding(clean(request.getBuilding()));
        kitchen.setInstagramLink(clean(request.getInstagramLink()));
        kitchen.setSellerType(sellerCategory);
        kitchen.setAreaId(serviceArea.getId());
        kitchen.setSocietyIds(request.getServiceSocietyIds());
        KitchenDto created = sellerService.createKitchen(kitchen, seller);
        String reservedSlug = created.getName();
        // Audit the slug reservation against the seller application (Step 7). The
        // slug is reserved now, while the application is still PENDING_APPROVAL.
        if (auditService != null) {
            auditService.record(AdminAuditService.KITCHEN_SLUG_RESERVED, seller, "KITCHEN",
                    created.getId(), request.getKitchenName().trim(), null, reservedSlug, null);
        }
        analyticsService.record(AnalyticsService.EV_USER_REGISTERED, seller.getId(), seller.getMobileNumber(),
                null, seller.getName());
        return new SellerRegistration(seller, reservedSlug);
    }

    @Transactional
    public User login(@Valid CredentialLoginRequestDto request) {
        requireDirectAuthEnabled();
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidCredentialsException();
        }
        User user = users.findByMobileNumber(request.getMobileNumber())
                .orElseThrow(InvalidCredentialsException::new);
        if (user.isBlocked() || !validPasswordFor(user, request.getPassword())) {
            throw new InvalidCredentialsException();
        }
        analyticsService.record(AnalyticsService.EV_USER_LOGIN, user.getId(), user.getMobileNumber(),
                null, user.getName());
        return user;
    }

    public boolean isDirectAuthEnabled() {
        return AuthSettings.passwordLoginEnabled(environment);
    }

    public boolean isAdminPasswordConfigured() {
        return AuthSettings.adminPasswordConfigured(environment);
    }

    public boolean isSuperAdminPasswordConfigured() {
        return AuthSettings.superAdminPasswordConfigured(environment);
    }

    @Transactional(readOnly = true)
    public java.util.List<CoverageOptionDto> registrationOptions() {
        return locations.getCoverageOptions();
    }

    private boolean validPasswordFor(User user, String rawPassword) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
            String property = user.getRole() == UserRole.SUPER_ADMIN
                    ? AuthSettings.SUPER_ADMIN_PASSWORD : AuthSettings.ADMIN_PASSWORD;
            String configured = environment.getProperty(property);
            return StringUtils.hasText(configured)
                    && java.security.MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8), configured.getBytes(StandardCharsets.UTF_8));
        }
        return StringUtils.hasText(user.getPasswordHash())
                && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private void requireDirectAuthEnabled() {
        if (!isDirectAuthEnabled()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Registration and password login are disabled on this server.");
        }
    }

    private void ensureMobileAvailable(String mobileNumber) {
        if (users.findByMobileNumber(mobileNumber).isPresent()) {
            throw new AccountAlreadyExistsException();
        }
    }

    private User saveNewUser(User user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AccountAlreadyExistsException();
        }
    }

    private void applyBuyerLocation(User buyer, Long areaId, Long societyId) {
        if (areaId == null && societyId == null) return;
        if (areaId == null || societyId == null) {
            throw new IllegalArgumentException("Select both an area and a society.");
        }
        Area area = requireActiveArea(areaId);
        Society society = requireActiveSociety(societyId, area);
        buyer.setAreaRef(area);
        buyer.setArea(area.getName());
        buyer.setSocietyRef(society);
        buyer.setSociety(society.getName());
    }

    private Area requireActiveArea(Long areaId) {
        Area area = locations.findArea(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Select a valid area."));
        if (!area.isActive()) throw new IllegalArgumentException("The selected area is no longer available.");
        return area;
    }

    private Society requireActiveSociety(Long societyId, Area area) {
        Society society = locations.findSociety(societyId)
                .orElseThrow(() -> new IllegalArgumentException("Select a valid society."));
        if (!society.isActive()) throw new IllegalArgumentException("The selected society is no longer available.");
        if (!area.getId().equals(society.getArea().getId())) {
            throw new IllegalArgumentException("The selected society does not belong to the selected area.");
        }
        return society;
    }

    private static void requireBcryptCompatible(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be no longer than 72 UTF-8 bytes.");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---- Forget-password flow (Part 2) -----------------------------------

    /** One-time bearer token. */
    private static String generateResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private String baseResetUrl() {
        String url = environment.getProperty("sociomart.public-base-url");
        return StringUtils.hasText(url) ? url : "http://localhost:8081";
    }

    private String buildResetLink(String token) {
        return baseResetUrl() + "/reset-password?token=" + token;
    }

    private static void logResetLink(User user, String resetLink) {
        LOGGER.info("[SOCIOMART-RESET] A single-use reset link for user '{}' (mobile {}): {}",
                user.getId(), user.getMobileNumber(), resetLink);
    }

    /**
     * Forgot-password: validates the request, issues a single-use token bound to the
     * user, and prints the reset link to the console/log for manual testing (no SMTP
     * relay is configured in this repository today).
     */
    @Transactional
    public PasswordResetResponseDto requestPasswordReset(String email) {
        requireDirectAuthEnabled();
        User user = users.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "No account is registered with that e-mail address."));
        PasswordResetToken token = new PasswordResetToken(
                user, generateResetToken(), LocalDateTime.now().plus(RESET_TOKEN_TTL));
        passwordResetTokens.save(token);
        String resetLink = buildResetLink(token.getToken());
        logResetLink(user, resetLink);
        return new PasswordResetResponseDto(
                "If an account exists for '" + email + "', a single-use reset link has been issued.",
                resetLink);
    }

    /**
     * Reset-password: validates the token (single-use + not expired), BCrypt-hashes the
     * new password, persists it, and invalidates the token.
     */
    @Transactional
    public User resetPassword(String token, String newPassword) {
        requireDirectAuthEnabled();
        requireBcryptCompatible(newPassword);
        PasswordResetToken reset = passwordResetTokens.findByToken(token)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired reset token."));
        if (reset.isUsed() || reset.isExpired()) {
            passwordResetTokens.delete(reset);
            throw new InvalidCredentialsException("Invalid or expired reset token.");
        }
        User user = reset.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        reset.use();
        passwordResetTokens.save(reset);
        passwordResetTokens.deleteByToken(token);
        analyticsService.record(AnalyticsService.EV_USER_PASSWORD_RESET,
                user.getId(), user.getMobileNumber(), null, user.getName());
        return user;
    }

    /** Response shape returned to the caller after a reset link is issued. */
    public record PasswordResetResponseDto(String message, String resetLink) {
    }
}
