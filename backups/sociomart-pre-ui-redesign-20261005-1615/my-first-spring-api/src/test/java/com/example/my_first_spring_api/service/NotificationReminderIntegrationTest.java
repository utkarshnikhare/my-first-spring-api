package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reminder-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.reminders.initial-delay-ms=3600000"
})
@ActiveProfiles("test")
class NotificationReminderIntegrationTest {
    @Autowired NotificationReminderJob job;
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired OrderRepository orders;
    @Autowired EnquiryRepository enquiries;
    @Autowired NotificationEventRepository notifications;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void loadsDetachedRelationshipsAndCreatesOnlyOneReminderPerRecord() {
        long before = notifications.count();
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            User seller = users.save(new User("Seller", "8000000001", null, UserRole.SELLER));
            User buyer = users.save(new User("Buyer", "8000000002", null, UserRole.BUYER));
            Kitchen kitchen = kitchens.save(new Kitchen("reminder-kitchen", "Kitchen", "", null, seller));
            Order order = new Order(buyer, kitchen);
            order.setOrderNumber(UUID.randomUUID().toString());
            order.setOrderStatus(OrderStatus.ORDERED);
            order.setOrderTime(LocalDateTime.now().minusHours(1));
            orders.save(order);
            Enquiry enquiry = new Enquiry();
            enquiry.setUser(buyer);
            enquiry.setKitchen(kitchen);
            enquiry.setMessage("A request");
            enquiries.saveAndFlush(enquiry);
            enquiry.setCreatedAt(LocalDateTime.now().minusHours(1));
            Order cancelled = new Order(buyer, kitchen);
            cancelled.setOrderNumber(UUID.randomUUID().toString());
            cancelled.setOrderStatus(OrderStatus.CANCELLED);
            cancelled.setOrderTime(LocalDateTime.now().minusHours(1));
            orders.save(cancelled);
            return new Long[] { order.getId(), enquiry.getId(), cancelled.getId() };
        });
        // Deliberately call after the setup transaction closes: real Hibernate proxies.
        job.sendUnacknowledgedReminders();
        job.sendUnacknowledgedReminders();
        assertThat(notifications.count()).isEqualTo(before + 2);
        assertThat(orders.findById(ids[0]).orElseThrow().getRemindedAt()).isNotNull();
        assertThat(enquiries.findById(ids[1]).orElseThrow().getRemindedAt()).isNotNull();
        assertThat(orders.findById(ids[2]).orElseThrow().getRemindedAt()).isNull();
    }
}
