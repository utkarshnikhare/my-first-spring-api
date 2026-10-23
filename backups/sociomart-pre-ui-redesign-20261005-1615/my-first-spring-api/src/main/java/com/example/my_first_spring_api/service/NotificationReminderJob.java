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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationReminderJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationReminderJob.class);
    private static final List<OrderStatus> WAITING = List.of(OrderStatus.ORDERED, OrderStatus.CONFIRMED);
    private final TransactionTemplate transaction;

    private final OrderRepository orderRepository;
    private final EnquiryRepository enquiryRepository;
    private final NotificationService notificationService;

    @Autowired
    public NotificationReminderJob(OrderRepository orderRepository, EnquiryRepository enquiryRepository,
                                   NotificationService notificationService, PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.enquiryRepository = enquiryRepository;
        this.notificationService = notificationService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${sociomart.reminders.delay-ms:300000}",
               initialDelayString = "${sociomart.reminders.initial-delay-ms:30000}")
    public void sendUnacknowledgedReminders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        // Bound each run. Successful records leave the candidate set; failures are retried.
        for (Long id : orderRepository.findReminderCandidates(WAITING, cutoff, PageRequest.of(0, 100))) {
            process("order", id, () -> orderRepository.findByIdForUpdate(id).ifPresent(order -> {
                if (order.getRemindedAt() != null || order.getAcknowledgedAt() != null || !WAITING.contains(order.getOrderStatus())) return;
                notificationService.sendReminder(order.getKitchen().getSeller(),
                        "Reminder: Unacknowledged SocioMart order",
                        "Order " + order.getOrderNumber() + " is still unacknowledged. Please review it."
                );
                order.setRemindedAt(LocalDateTime.now());
            }));
        }
        for (Long id : enquiryRepository.findReminderCandidates(EnquiryStatus.NEW, cutoff, PageRequest.of(0, 100))) {
            process("enquiry", id, () -> enquiryRepository.findByIdForUpdate(id).ifPresent(enquiry -> {
                if (enquiry.getRemindedAt() != null || enquiry.getAcknowledgedAt() != null || enquiry.getStatus() != EnquiryStatus.NEW) return;
                notificationService.sendReminder(enquiry.getKitchen().getSeller(),
                        "Reminder: Unacknowledged SocioMart enquiry",
                        "Enquiry " + enquiry.getId() + " from " + enquiry.getUser().getName() + " is waiting for your response."
                );
                enquiry.setRemindedAt(LocalDateTime.now());
            }));
        }
    }

    private void process(String type, Long id, Runnable work) {
        try {
            // Query, lazy access, event creation and marker commit together for one record.
            transaction.executeWithoutResult(status -> work.run());
        } catch (RuntimeException ex) {
            log.warn("Could not create {} reminder for id {} ({}); will retry", type, id, ex.getClass().getSimpleName());
        }
    }
}
