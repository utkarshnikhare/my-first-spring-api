package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.EnquiryDto;
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

    /** Body: { "kitchenId": 1, "message": "Do you make gluten-free parathas?" } */
    @PostMapping
    public ResponseEntity<EnquiryDto> submit(@RequestBody Map<String, Object> body, HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        Long kitchenId = Long.valueOf(String.valueOf(body.get("kitchenId")));
        String message = String.valueOf(body.getOrDefault("message", ""));
        return ResponseEntity.ok(enquiryService.submit(buyer, kitchenId, message));
    }
}
