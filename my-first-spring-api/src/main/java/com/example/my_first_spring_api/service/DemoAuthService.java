package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.SecurityConfig;
import com.example.my_first_spring_api.dto.BuyerRegistrationRequestDto;
import com.example.my_first_spring_api.dto.CredentialLoginRequestDto;
import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.dto.KitchenCreateDto;
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
public class DemoAuthService {
    private final UserRepository users;
    private final KitchenRepository kitchens;
    private final LocationService locations;
    private final SellerService sellerService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final AnalyticsService analyticsService;

    public DemoAuthService(UserRepository users, KitchenRepository kitchens, LocationService locations,
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
        Area primaryArea = requireActiveArea(request.getPrimaryAreaId());
        Society primarySociety = requireActiveSociety(request.getPrimarySocietyId(), primaryArea);
        Area serviceArea = requireActiveArea(request.getServiceAreaId());
        String kitchenSlug = request.getKitchenSlug().trim().toLowerCase(Locale.ROOT);
        if (kitchens.findByName(kitchenSlug).isPresent()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "That kitchen URL is already in use.");
        }

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
        kitchen.setName(kitchenSlug);
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
        sellerService.createKitchen(kitchen, seller);
        analyticsService.record(AnalyticsService.EV_USER_REGISTERED, seller.getId(), seller.getMobileNumber(),
                null, seller.getName());
        return new SellerRegistration(seller, kitchenSlug);
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
        return SecurityConfig.isDirectAuthEnabled(environment);
    }

    public boolean isAdminPasswordConfigured() {
        return StringUtils.hasText(environment.getProperty("sociomart.demo.admin-password"));
    }

    public boolean isSuperAdminPasswordConfigured() {
        return StringUtils.hasText(environment.getProperty("sociomart.demo.super-admin-password"));
    }

    @Transactional(readOnly = true)
    public java.util.List<CoverageOptionDto> registrationOptions() {
        return locations.getCoverageOptions();
    }

    private boolean validPasswordFor(User user, String rawPassword) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
            String property = user.getRole() == UserRole.SUPER_ADMIN
                    ? "sociomart.demo.super-admin-password" : "sociomart.demo.admin-password";
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
                    "Direct registration and login are available only in the configured demo environment.");
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
