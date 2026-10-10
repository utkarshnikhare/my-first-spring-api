package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.AuthResponseDto;
import com.example.my_first_spring_api.dto.BuyerRegistrationRequestDto;
import com.example.my_first_spring_api.dto.CredentialLoginRequestDto;
import com.example.my_first_spring_api.dto.SellerRegistrationRequestDto;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.DemoAuthService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final BuyerService buyerService;
    private final DemoAuthService demoAuthService;
    private final SecurityContextRepository securityContextRepository;

    @Autowired
    public AuthController(BuyerService buyerService, DemoAuthService demoAuthService,
                          SecurityContextRepository securityContextRepository) {
        this.buyerService = buyerService;
        this.demoAuthService = demoAuthService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/config")
    public Map<String, Boolean> config() {
        return Map.of(
                "directAuthEnabled", demoAuthService.isDirectAuthEnabled(),
                "demoRegistrationEnabled", demoAuthService.isDirectAuthEnabled(),
                "adminLoginConfigured", demoAuthService.isAdminPasswordConfigured(),
                "superAdminLoginConfigured", demoAuthService.isSuperAdminPasswordConfigured());
    }

    @GetMapping("/registration-options")
    public ResponseEntity<?> registrationOptions() {
        return ResponseEntity.ok(demoAuthService.registrationOptions());
    }

    @PostMapping("/register/buyer")
    public ResponseEntity<AuthResponseDto> registerBuyer(@Valid @RequestBody BuyerRegistrationRequestDto request,
                                                           HttpServletRequest servletRequest,
                                                           HttpServletResponse response) {
        User buyer = demoAuthService.registerBuyer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticate(buyer, "Registration successful",
                servletRequest, response));
    }

    @PostMapping("/register/seller")
    public ResponseEntity<AuthResponseDto> registerSeller(@Valid @RequestBody SellerRegistrationRequestDto request,
                                                            HttpServletRequest servletRequest,
                                                            HttpServletResponse response) {
        DemoAuthService.SellerRegistration registration = demoAuthService.registerSeller(request);
        AuthResponseDto result = authenticate(registration.user(), "Registration submitted for Admin approval",
                servletRequest, response);
        result.setKitchenSlug(registration.kitchenSlug());
        result.setKitchenUrl("/index.html#/kitchen/" + registration.kitchenSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody CredentialLoginRequestDto request,
                                                  HttpServletRequest servletRequest,
                                                  HttpServletResponse response) {
        User user = demoAuthService.login(request);
        return ResponseEntity.ok(authenticate(user, "Logged in successfully", servletRequest, response));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDto> me(HttpServletRequest request) {
        User user = buyerService.getCurrentBuyer(request.getSession(false));
        if (user == null) {
            return ResponseEntity.ok(new AuthResponseDto(false, "Not authenticated", null, null, null, null, null));
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
}
