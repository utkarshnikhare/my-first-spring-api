package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.EnquiryDto;
import com.example.my_first_spring_api.model.EnquiryStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.EnquiryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Session-gated buyer enquiries (identity-bound action). */
@RestController
@RequestMapping("/api/enquiries")
public class EnquiryController {

    private final EnquiryService enquiryService;
    private final BuyerService buyerService;

    @Autowired
    public EnquiryController(EnquiryService enquiryService, BuyerService buyerService) {
        this.enquiryService = enquiryService;
        this.buyerService = buyerService;
    }

    @GetMapping("/my")
    public ResponseEntity<List<EnquiryDto>> getMyEnquiries(HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(enquiryService.getEnquiries(buyer));
    }

    /** Body: { "kitchenId": 1, "message": "...", "preferredDate": "2026-09-15", "quantity": "2 kg", "referenceImageUrl": "https://..." } */
    @PostMapping
    public ResponseEntity<EnquiryDto> submit(@RequestBody Map<String, Object> body, HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        Long kitchenId = Long.valueOf(String.valueOf(body.get("kitchenId")));
        String message = String.valueOf(body.getOrDefault("message", ""));
        String preferredDate = body.containsKey("preferredDate") ? String.valueOf(body.get("preferredDate")) : null;
        String quantity = body.containsKey("quantity") ? String.valueOf(body.get("quantity")) : null;
        String referenceImageUrl = body.containsKey("referenceImageUrl") ? String.valueOf(body.get("referenceImageUrl")) : null;
        return ResponseEntity.ok(enquiryService.submit(buyer, kitchenId, message, preferredDate, quantity, referenceImageUrl));
    }

    /** Seller: get enquiries for my store */
    @GetMapping("/seller/my")
    public ResponseEntity<List<EnquiryDto>> getSellerEnquiries(HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(enquiryService.getSellerEnquiries(seller));
    }

    /** Seller: acknowledge enquiry */
    @PostMapping("/{enquiryId}/acknowledge")
    public ResponseEntity<EnquiryDto> acknowledge(@PathVariable Long enquiryId, HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(enquiryService.acknowledge(enquiryId, seller));
    }

    /** Seller: update enquiry status */
    @PatchMapping("/{enquiryId}/status")
    public ResponseEntity<EnquiryDto> updateStatus(@PathVariable Long enquiryId, @RequestBody Map<String, String> body, HttpSession session) {
        User seller = requireSeller(session);
        EnquiryStatus status = EnquiryStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(enquiryService.updateStatus(enquiryId, status, seller));
    }

    private User requireSeller(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || user.getRole() != com.example.my_first_spring_api.model.UserRole.SELLER) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Seller access required");
        }
        return user;
    }
}
