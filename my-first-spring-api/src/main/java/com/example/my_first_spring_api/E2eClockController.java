package com.example.my_first_spring_api;

import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.service.BuyerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/e2e/clock")
@Profile("e2e")
public class E2eClockController {
    private final BuyerService buyerService;

    public E2eClockController(BuyerService buyerService) {
        this.buyerService = buyerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> setClock(@RequestBody ClockRequest request,
                                                         HttpSession session) {
        requireAdmin(session);
        if (request == null || request.now() == null) {
            throw new IllegalArgumentException("A local date-time is required.");
        }
        AppTimeZone.setTestTime(request.now());
        return ResponseEntity.ok(Map.of("now", AppTimeZone.now().toString()));
    }

    @DeleteMapping
    public ResponseEntity<Void> resetClock(HttpSession session) {
        requireAdmin(session);
        AppTimeZone.resetTestTime();
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(HttpSession session) {
        User user = buyerService.getCurrentBuyer(session);
        if (user == null) {
            throw new BuyerNotAuthenticatedException("Authentication required to control the E2E clock.");
        }
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SUPER_ADMIN) {
            throw new SellerNotAuthorizedException("An Admin session is required to control the E2E clock.");
        }
    }

    public record ClockRequest(LocalDateTime now) {}
}
