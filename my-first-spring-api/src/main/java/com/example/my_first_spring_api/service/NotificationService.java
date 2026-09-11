package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.NotificationEvent;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.NotificationEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationEventRepository notificationEventRepository;

    @Autowired
    public NotificationService(NotificationEventRepository notificationEventRepository) {
        this.notificationEventRepository = notificationEventRepository;
    }

    public void sendNewOrderNotification(User seller, String orderNumber, String productSummary) {
        createEvent(seller, "New SocioMart order",
                "You received an order for " + productSummary + ".\nOpen SocioMart to review and confirm.");
    }

    public void sendNewEnquiryNotification(User seller, String buyerName) {
        createEvent(seller, "New SocioMart enquiry",
                buyerName + " has sent a custom request.\nOpen SocioMart to view the enquiry.");
    }

    public void sendReminder(User seller, String title, String body) {
        createEvent(seller, title, body);
    }

    private void createEvent(User user, String title, String body) {
        NotificationEvent event = new NotificationEvent();
        event.setUserId(user.getId());
        event.setUserMobile(user.getMobileNumber());
        event.setTitle(title);
        event.setBody(body);
        event.setDelivered(false);
        notificationEventRepository.save(event);
    }
}
