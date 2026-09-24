package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.NotificationEventDto;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final BuyerService buyerService;

    @Autowired
    public NotificationController(NotificationService notificationService, BuyerService buyerService) {
        this.notificationService = notificationService;
        this.buyerService = buyerService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationEventDto>> unread(HttpSession session) {
        User user = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(notificationService.getUnread(user));
    }

    @PatchMapping("/{eventId}/read")
    public ResponseEntity<NotificationEventDto> markRead(@PathVariable Long eventId, HttpSession session) {
        User user = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(notificationService.markRead(eventId, user));
    }
}
