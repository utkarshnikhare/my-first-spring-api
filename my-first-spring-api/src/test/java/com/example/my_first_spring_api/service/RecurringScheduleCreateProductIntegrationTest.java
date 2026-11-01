package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.RecurringScheduleDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OccurrenceRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.RecurringScheduleRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recurring-create-product;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class RecurringScheduleCreateProductIntegrationTest {

    @Autowired
    private SellerService sellerService;
    @Autowired
    private FeatureService featureService;
    @Autowired
    private UserRepository users;
    @Autowired
    private KitchenRepository kitchens;
    @Autowired
    private ProductRepository products;
    @Autowired
    private RecurringScheduleRepository schedules;
    @Autowired
    private OccurrenceRepository occurrences;

    private User seller;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        seller = users.save(new User("RecurringSeller" + suffix, "91" + suffix, null, UserRole.SELLER));
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        users.save(seller);
        kitchen = kitchens.save(new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller));

        // The "test" profile skips DataInitializer, so seed the feature catalogue
        // explicitly before enabling the preorders feature for this seller.
        featureService.ensureDefaults();
        featureService.setSellerGrant(seller.getId(), FeatureService.KEY_PREORDERS, true, null);
    }

    @Test
    void createProductRejectsNewRecurringScheduleWithoutPersistingAnything() {
        ProductCreateDto dto = productDtoBuilder();
        dto.setRecurringSchedule(recurringScheduleDto());
        long scheduleCountBefore = schedules.count();
        long occurrenceCountBefore = occurrences.count();

        assertThatThrownBy(() -> sellerService.createProduct(kitchen.getId(), dto, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Creating new recurring schedules is no longer supported");
        assertThat(products.findByKitchen(kitchen)).isEmpty();
        assertThat(schedules.count()).isEqualTo(scheduleCountBefore);
        assertThat(occurrences.count()).isEqualTo(occurrenceCountBefore);
    }

    @Test
    void createProductRejectsInvalidRecurringScheduleBeforePersistence() {
        ProductCreateDto dto = productDtoBuilder();
        dto.setRecurringSchedule(invalidRecurringScheduleDto());

        long scheduleCountBefore = schedules.count();
        long occurrenceCountBefore = occurrences.count();

        assertThatThrownBy(() -> sellerService.createProduct(kitchen.getId(), dto, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Creating new recurring schedules is no longer supported");

        // No product, schedule, or occurrence is written for a rejected request.
        assertThat(products.findByKitchen(kitchen)).isEmpty();
        assertThat(schedules.count()).isEqualTo(scheduleCountBefore);
        assertThat(occurrences.count()).isEqualTo(occurrenceCountBefore);
    }

    private ProductCreateDto productDtoBuilder() {
        ProductCreateDto dto = new ProductCreateDto();
        dto.setName("Poha");
        dto.setPrice(BigDecimal.valueOf(25));
        dto.setCategories(java.util.List.of("BREAKFAST"));
        dto.setAvailableDate(LocalDate.now().plusDays(1));
        dto.setOrderWindowEnd("23:58");
        dto.setReadyByTime("1:00 PM tomorrow");
        return dto;
    }

    private RecurringScheduleDto recurringScheduleDto() {
        RecurringScheduleDto recurring = new RecurringScheduleDto();
        recurring.setStartDate(productDtoBuilder().getAvailableDate());
        recurring.setEndDate(LocalDate.of(2026, 11, 5));
        recurring.setRecurrenceWeekdays(EnumSet.of(DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        recurring.setDefaultQuantity(14);
        recurring.setDefaultOrderCloseTime("13:00");
        recurring.setDefaultReadyByTime("3:00 PM");
        return recurring;
    }

    private RecurringScheduleDto invalidRecurringScheduleDto() {
        RecurringScheduleDto recurring = new RecurringScheduleDto();
        recurring.setStartDate(LocalDate.of(2026, 10, 6));
        recurring.setEndDate(LocalDate.of(2026, 11, 5));
        recurring.setRecurrenceWeekdays(EnumSet.noneOf(DayOfWeek.class));
        return recurring;
    }
}
