package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Enquiry;
import com.example.my_first_spring_api.model.EnquiryStatus;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.repository.EnquiryRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationReminderJob {

    private final OrderRepository orderRepository;
    private final EnquiryRepository enquiryRepository;
    private final NotificationService notificationService;

    @Autowired
    public NotificationReminderJob(OrderRepository orderRepository, EnquiryRepository enquiryRepository, NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.enquiryRepository = enquiryRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 300000)
    public void sendUnacknowledgedReminders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<Order> orders = orderRepository.findUnacknowledgedOrdersExcludingStatus(OrderStatus.DRAFT, cutoff);
        for (Order order : orders) {
            Kitchen kitchen = order.getKitchen();
            if (kitchen != null && kitchen.getSeller() != null) {
                notificationService.sendReminder(
                        kitchen.getSeller(),
                        "Reminder: Unacknowledged SocioMart order",
                        "Order " + order.getOrderNumber() + " is still unacknowledged. Please review it."
                );
            }
        }
        List<Enquiry> enquiries = enquiryRepository.findUnacknowledgedEnquiries(EnquiryStatus.NEW, cutoff);
        for (Enquiry enquiry : enquiries) {
            if (enquiry.getKitchen() != null && enquiry.getKitchen().getSeller() != null) {
                notificationService.sendReminder(
                        enquiry.getKitchen().getSeller(),
                        "Reminder: Unacknowledged SocioMart enquiry",
                        "An enquiry from " + (enquiry.getUser() != null ? enquiry.getUser().getName() : "a buyer") + " is waiting for your response."
                );
            }
        }
    }
}
