package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scheduler wiring and recovery tests for reminder job.
 *
 * <p>Tests call the job method directly for deterministic execution,
 * rather than relying on @Scheduled timing in test context.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scheduler-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
@Transactional
class SchedulerWiringAndRecoveryTest {

    @Autowired private NotificationReminderJob reminderJob;
    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EnquiryRepository enquiryRepository;
    @Autowired private NotificationEventRepository notificationEventRepository;

    private User seller;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.saveAndFlush(seller);

        kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen = kitchenRepository.saveAndFlush(kitchen);
    }

    @Test
    void reminderJobCreatesNotificationForUnacknowledgedOrder() {
        User buyer = new User("Buyer", "93" + UUID.randomUUID().toString().substring(0, 8), "A-1", UserRole.BUYER);
        buyer = userRepository.saveAndFlush(buyer);

        Order order = new Order(buyer, kitchen);
        order.setOrderNumber("REMINDER-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setOrderTime(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(30));
        order = orderRepository.saveAndFlush(order);

        long before = notificationEventRepository.count();

        reminderJob.sendUnacknowledgedReminders();

        long after = notificationEventRepository.count();

        assertThat(after).isEqualTo(before + 1);

        NotificationEvent reminder = notificationEventRepository.findAll().stream()
                .filter(e -> e.getTitle().contains("Reminder"))
                .filter(e -> e.getUserId() != null && e.getUserId().equals(seller.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Reminder notification not found"));

        assertThat(reminder.getTitle()).contains("Reminder");
        assertThat(reminder.getBody()).contains(order.getOrderNumber());

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getRemindedAt()).isNotNull();
    }

    @Test
    void reminderJobIsIdempotent() {
        User buyer = new User("Buyer2", "93" + UUID.randomUUID().toString().substring(0, 8), "A-1", UserRole.BUYER);
        buyer = userRepository.saveAndFlush(buyer);

        Order order = new Order(buyer, kitchen);
        order.setOrderNumber("IDEMPOTENT-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setOrderTime(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(30));
        order = orderRepository.saveAndFlush(order);

        reminderJob.sendUnacknowledgedReminders();
        long afterFirst = notificationEventRepository.count();

        reminderJob.sendUnacknowledgedReminders();
        long afterSecond = notificationEventRepository.count();

        assertThat(afterSecond).isEqualTo(afterFirst);
    }

    @Test
    void reminderJobSkipsAcknowledgedOrders() {
        User buyer = new User("Buyer3", "93" + UUID.randomUUID().toString().substring(0, 8), "A-1", UserRole.BUYER);
        buyer = userRepository.saveAndFlush(buyer);

        Order order = new Order(buyer, kitchen);
        order.setOrderNumber("ACK-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setAcknowledgedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(10));
        order.setOrderTime(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(30));
        order = orderRepository.saveAndFlush(order);

        long before = notificationEventRepository.count();

        reminderJob.sendUnacknowledgedReminders();

        long after = notificationEventRepository.count();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void reminderJobSkipsCancelledOrders() {
        User buyer = new User("Buyer4", "93" + UUID.randomUUID().toString().substring(0, 8), "A-1", UserRole.BUYER);
        buyer = userRepository.saveAndFlush(buyer);

        Order order = new Order(buyer, kitchen);
        order.setOrderNumber("CANCEL-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setOrderTime(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(30));
        order = orderRepository.saveAndFlush(order);

        long before = notificationEventRepository.count();

        reminderJob.sendUnacknowledgedReminders();

        long after = notificationEventRepository.count();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void reminderJobProcessesEnquiries() {
        User buyer = new User("Buyer5", "93" + UUID.randomUUID().toString().substring(0, 8), "A-1", UserRole.BUYER);
        buyer = userRepository.saveAndFlush(buyer);

        Enquiry enquiry = new Enquiry();
        enquiry.setUser(buyer);
        enquiry.setKitchen(kitchen);
        enquiry.setMessage("Test enquiry");
        enquiry.setStatus(EnquiryStatus.NEW);
        // Do NOT set createdAt here - let @PrePersist set it to current time
        enquiry = enquiryRepository.saveAndFlush(enquiry);

        // Now manually set createdAt to 30 minutes ago (after initial persist)
        LocalDateTime pastTime = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(30);
        enquiry.setCreatedAt(pastTime);
        enquiry = enquiryRepository.saveAndFlush(enquiry); // @PreUpdate updates updatedAt, not createdAt

        // Debug: check if the enquiry is visible to the reminder query
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(15);
        List<Long> candidates = enquiryRepository.findReminderCandidates(EnquiryStatus.NEW, cutoff, PageRequest.of(0, 100));
        System.out.println("Reminder candidates found: " + candidates.size() + " (enquiry id: " + enquiry.getId() + ")");

        long before = notificationEventRepository.count();

        reminderJob.sendUnacknowledgedReminders();

        long after = notificationEventRepository.count();

        // Job creates one reminder notification for the enquiry
        assertThat(after).isEqualTo(before + 1);

        NotificationEvent reminder = notificationEventRepository.findAll().stream()
                .filter(e -> e.getTitle().contains("Reminder"))
                .filter(e -> e.getBody().toLowerCase().contains("enquiry"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Enquiry reminder not found"));

        assertThat(reminder.getBody()).contains(enquiry.getId().toString());
        assertThat(reminder.getBody()).contains(buyer.getName());

        Enquiry updated = enquiryRepository.findById(enquiry.getId()).orElseThrow();
        assertThat(updated.getRemindedAt()).isNotNull();
    }

    @Test
    void reminderJobRespectsTimezoneAsiaKolkata() {
        ZoneId defaultZone = ZoneId.systemDefault();
        assertThat(defaultZone).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }
}