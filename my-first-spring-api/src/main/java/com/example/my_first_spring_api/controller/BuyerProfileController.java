package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.BuyerProfileDto;
import com.example.my_first_spring_api.service.BuyerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/buyer/profile")
public class BuyerProfileController {

    private final BuyerService buyerService;

    @Autowired
    public BuyerProfileController(BuyerService buyerService) {
        this.buyerService = buyerService;
    }

    @GetMapping
    public ResponseEntity<BuyerProfileDto> getProfile(HttpSession session) {
        return ResponseEntity.ok(buyerService.getProfile(session));
    }

    @PutMapping
    public ResponseEntity<BuyerProfileDto> updateProfile(@RequestBody BuyerProfileDto profileDto, HttpSession session) {
        return ResponseEntity.ok(buyerService.updateProfile(profileDto, session));
    }
}
