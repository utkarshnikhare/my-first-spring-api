package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.AuthResponseDto;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final BuyerService buyerService;
    private final SecurityContextRepository securityContextRepository;

    @Autowired
    public AuthController(BuyerService buyerService, SecurityContextRepository securityContextRepository) {
        this.buyerService = buyerService;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Demo login: authenticates a buyer/seller by mobile number only (no code step).
     * For client demo so the app opens and operates with a mobile number only.
     */
    @PostMapping("/demo-login")
    public ResponseEntity<AuthResponseDto> demoLogin(@Valid @RequestBody Map<String, String> body,
                                                      HttpServletRequest request,
                                                      HttpServletResponse response) {
        String mobileNumber = body.getOrDefault("mobileNumber", "");
        String name = body.get("name");
        String flatHouseNumber = body.get("flatHouseNumber");

        User buyer = buyerService.demoLoginAndAuthenticate(mobileNumber, name, flatHouseNumber, request.getSession(true));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                buyer.getId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + buyer.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(new AuthResponseDto(
                true, "Logged in successfully",
                buyer.getId(), buyer.getName(), buyer.getMobileNumber(),
                buyer.getFlatHouseNumber(), buyer.getRole().name(), buyer.getSellerApprovalStatus()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDto> me(HttpServletRequest request) {
        User buyer = buyerService.getCurrentBuyer(request.getSession(false));
        if (buyer == null) {
            return ResponseEntity.ok(new AuthResponseDto(false, "Not authenticated", null, null, null, null, null));
        }
        return ResponseEntity.ok(new AuthResponseDto(
                true, "Authenticated", buyer.getId(), buyer.getName(),
                buyer.getMobileNumber(), buyer.getFlatHouseNumber(), buyer.getRole().name(),
                buyer.getSellerApprovalStatus()));
    }

    @PostMapping("/become-seller")
    public ResponseEntity<AuthResponseDto> becomeSeller(HttpServletRequest request, HttpServletResponse response) {
        User user = buyerService.becomeSeller(request.getSession());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(new AuthResponseDto(
                true, "Seller account created — pending admin approval", user.getId(), user.getName(),
                user.getMobileNumber(), user.getFlatHouseNumber(), user.getRole().name(),
                user.getSellerApprovalStatus()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            buyerService.logout(session);
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
