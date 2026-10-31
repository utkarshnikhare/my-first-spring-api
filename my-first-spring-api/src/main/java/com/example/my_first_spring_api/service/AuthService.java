package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AuthSettings;
import com.example.my_first_spring_api.dto.BuyerRegistrationRequestDto;
import com.example.my_first_spring_api.dto.CredentialLoginRequestDto;
import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.SellerRegistrationRequestDto;
import com.example.my_first_spring_api.exception.AccountAlreadyExistsException;
import com.example.my_first_spring_api.exception.InvalidCredentialsException;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {
    public static final String EMAIL_PASSWORD_RECOVERY_UNAVAILABLE =
            "Email-based password recovery is unavailable. Use mobile recovery or contact support.";
    private final UserRepository users;
    private final KitchenRepository kitchens;
    private final LocationService locations;
    private final SellerService sellerService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final AnalyticsService analyticsService;
    /** Append-only audit for the slug reservation. Field-injected (optional) so manual test construction keeps working. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AdminAuditService auditService;
    /** Manual (Admin-mediated) password recovery integration; optional for the same reason as above. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PasswordRecoveryService passwordRecovery;
    /** Same minimum password policy as registration (10-72 characters). */
    private static final int MIN_PASSWORD_LENGTH = 10;

    public AuthService(UserRepository users, KitchenRepository kitchens, LocationService locations,
                       SellerService sellerService, PasswordEncoder passwordEncoder, Environment environment,
                       AnalyticsService analyticsService) {
        this.users = users;
        this.kitchens = kitchens;
        this.locations = locations;
        this.sellerService = sellerService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.analyticsService = analyticsService;
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
        var paymentMode = SellerPaymentConfiguration.parseMode(request.getAcceptedPaymentMode());
        if (paymentMode == null) {
            throw new IllegalArgumentException("Choose a seller payment option during registration.");
        }
        var gpayIdentifierType =
                SellerPaymentConfiguration.parseGpayIdentifierType(request.getGpayIdentifierType());
        SellerPaymentConfiguration.validate(paymentMode, request.getUpiId(),
                gpayIdentifierType, request.getGpayIdentifier());
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
        kitchen.setAcceptedPaymentMode(paymentMode.name());
        kitchen.setUpiId(clean(request.getUpiId()));
        kitchen.setGpayIdentifierType(gpayIdentifierType == null ? null : gpayIdentifierType.name());
        kitchen.setGpayIdentifier(clean(request.getGpayIdentifier()));
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

    /**
     * Outcome of a credential login: the authenticated user plus whether the
     * one-time temporary password of the manual recovery flow was used.
     */
    public record LoginResult(User user, boolean tempPasswordLogin) {
    }

    @Transactional
    public User login(@Valid CredentialLoginRequestDto request) {
        return loginWithOutcome(request).user();
    }

    @Transactional
    public LoginResult loginWithOutcome(@Valid CredentialLoginRequestDto request) {
        requireDirectAuthEnabled();
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidCredentialsException();
        }
        User user = users.findByMobileNumber(request.getMobileNumber())
                .orElseThrow(InvalidCredentialsException::new);
        if (user.isBlocked()) {
            throw new InvalidCredentialsException();
        }
        boolean standardPassword = validPasswordFor(user, request.getPassword());
        // Manual recovery (Admin-mediated): a one-time temporary password issued by a
        // Super Admin is a valid credential for its FIRST USE ONLY. It never lives in
        // users.password_hash, so the 24-hour expiry and single-use rules are absolute.
        boolean tempPasswordLogin = false;
        if (!standardPassword && passwordRecovery != null && user.getRole() != UserRole.BUYER) {
            tempPasswordLogin = passwordRecovery.consumeTempPasswordOnLogin(user, request.getPassword());
        }
        if (!standardPassword && !tempPasswordLogin) {
            throw new InvalidCredentialsException();
        }
        // Reached only when a credential is CORRECT, so this specific message can
        // never be used to probe which mobile numbers are registered (contrast a
        // blocked account, which stays on the generic branch above so login remains
        // useless for probing - see SecurityHardeningHttpTest). A seller awaiting
        // admin approval is told exactly why login fails instead of receiving a
        // vague "invalid credentials" answer.
        if (user.getRole() == UserRole.SELLER
                && user.getSellerApprovalStatus() == SellerApprovalStatus.PENDING) {
            throw new InvalidCredentialsException("Your account is pending admin approval");
        }
        analyticsService.record(AnalyticsService.EV_USER_LOGIN, user.getId(), user.getMobileNumber(),
                null, user.getName());
        return new LoginResult(user, tempPasswordLogin);
    }

    /**
     * Changes the password of a logged-in user (forced change after a temporary
     * password login, or a voluntary change). The current password may be the
     * user's original password OR the temporary password that opened this session
     * (it was consumed at first use but still identifies the session).
     */
    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword, String confirmPassword) {
        requireBcryptCompatible(newPassword);
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New password must be at least 10 characters long.");
        }
        if (!java.util.Objects.equals(newPassword, confirmPassword)) {
            throw new IllegalArgumentException("New password and confirmation do not match.");
        }
        boolean currentPasswordOk = validPasswordFor(user, currentPassword)
                || (passwordRecovery != null && passwordRecovery.verifyTempPasswordFor(user, currentPassword));
        if (!currentPasswordOk) {
            throw new InvalidCredentialsException("Your current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        if (auditService != null) {
            auditService.record(AdminAuditService.PASSWORD_CHANGED, user, "USER", user.getId(),
                    user.getMobileNumber(), null, "CHANGED",
                    "Password changed through the manual recovery forced-change flow");
        }
        if (passwordRecovery != null) {
            passwordRecovery.recordPasswordChangedAnalytics(user);
        }
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
            if (StringUtils.hasText(configured)
                    && java.security.MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8), configured.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
            return StringUtils.hasText(user.getPasswordHash())
                    && passwordEncoder.matches(rawPassword, user.getPasswordHash());
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

}
