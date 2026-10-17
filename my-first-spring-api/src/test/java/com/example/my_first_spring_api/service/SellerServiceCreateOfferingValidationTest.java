package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderItemRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Targeted tests for the Create Offering timing & availability behaviour:
 * optional Orders Open, required Orders Close / Delivery, blank quantity =
 * unlimited, and the impossible-combination business validations.
 */
class SellerServiceCreateOfferingValidationTest {

    @Mock ProductRepository productRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock UserRepository userRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock AnalyticsService analyticsService;
    @Mock OrderService orderService;
    @Mock FeatureService featureService;
    @Mock HttpSession httpSession;

    @InjectMocks SellerService sellerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(approvedKitchen()));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        allowFutureMenus();
    }

    private User approvedSeller() {
        User seller = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        seller.setId(10L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return seller;
    }

    private Kitchen approvedKitchen() {
        User seller = approvedSeller();
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        return kitchen;
    }

    private ProductCreateDto validDto() {
        ProductCreateDto dto = new ProductCreateDto();
        dto.setName("Poha");
        dto.setPrice(BigDecimal.valueOf(40));
        dto.setCategories(List.of("BREAKFAST"));
        dto.setAvailableDate(LocalDate.now().plusDays(1));
        dto.setOrderWindowEnd("23:58");
        dto.setReadyByTime(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(23, 59)).toString());
        return dto;
    }

    private void allowFutureMenus() {
        lenient().when(featureService.sellerHasAccess(any(User.class), anyString())).thenReturn(true);
        lenient().when(featureService.sellerLimit(any(User.class), anyString())).thenReturn(7);
    }

    @Test
    void blankOrdersOpenIsAcceptedEndToEndAndPersistedAsNull() {
        ProductCreateDto dto = validDto(); // no orderWindowStart set at all
        dto.setOrderWindowStart("");       // blank string from the form must also work
        var result = sellerService.createProduct(1L, dto, approvedSeller());
        assertThat(result.getOrderWindowStart()).isNull();
        assertThat(result.getOrderWindowEnd()).isEqualTo("23:58");
        assertThat(result.getReadyByTime()).isEqualTo(validDto().getReadyByTime());
    }

    @Test
    void populatedOrdersOpenIsPersisted() {
        ProductCreateDto dto = validDto();
        dto.setOrderWindowStart("08:00");
        var result = sellerService.createProduct(1L, dto, approvedSeller());
        assertThat(result.getOrderWindowStart()).isEqualTo("08:00");
    }

    @Test
    void blankQuantityMeansUnlimited() {
        var result = sellerService.createProduct(1L, validDto(), approvedSeller());
        assertThat(result.getMaxQuantity()).isNull();
        assertThat(result.getRemainingQuantity()).isNull();
    }

    @Test
    void limitedQuantityInitializesRemainingStock() {
        ProductCreateDto dto = validDto();
        dto.setMaxQuantity(5);
        var result = sellerService.createProduct(1L, dto, approvedSeller());
        assertThat(result.getMaxQuantity()).isEqualTo(5);
        assertThat(result.getRemainingQuantity()).isEqualTo(5);
    }

    @Test
    void negativeQuantityRejected() {
        ProductCreateDto dto = validDto();
        dto.setMaxQuantity(-2);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sellerService.createProduct(1L, dto, approvedSeller()));
        assertThat(ex.getMessage()).contains("negative");
    }

    @Test
    void ordersOpenAfterOrdersCloseBlocked() {
        ProductCreateDto dto = validDto();
        dto.setOrderWindowStart("11:00");
        dto.setOrderWindowEnd("10:00");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sellerService.createProduct(1L, dto, approvedSeller()));
        assertThat(ex.getMessage()).contains("Orders Open");
    }

    @Test
    void ordersCloseAfterDeliveryTimeBlocked() {
        ProductCreateDto dto = validDto();
        dto.setOrderWindowEnd("14:00");
        dto.setReadyByTime("1:00 PM today");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sellerService.createProduct(1L, dto, approvedSeller()));
        assertThat(ex.getMessage()).contains("Delivery");
    }

    @Test
    void deliveryDateBeforeOfferingDateBlocked() {
        allowFutureMenus();
        ProductCreateDto dto = validDto();
        dto.setAvailableDate(LocalDate.now().plusDays(3));
        dto.setReadyByTime("1:00 PM today"); // delivery today < offering in 3 days
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sellerService.createProduct(1L, dto, approvedSeller()));
        assertThat(ex.getMessage()).contains("cannot be before");
    }

    @Test
    void offeringForTomorrowAcceptedWhenFeatureAllows() {
        allowFutureMenus();
        ProductCreateDto dto = validDto();
        dto.setAvailableDate(LocalDate.now().plusDays(1));
        dto.setReadyByTime("1:00 PM tomorrow");
        var result = sellerService.createProduct(1L, dto, approvedSeller());
        assertThat(result.getAvailableDate()).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(result.getReadyByTime()).isEqualTo("1:00 PM tomorrow");
    }

    @Test
    void deliveryTodayForTomorrowOfferingBlocked() {
        // Regression: "today" in readyByTime must count as TODAY (not tomorrow),
        // so an offering available tomorrow cannot promise delivery today.
        allowFutureMenus();
        ProductCreateDto dto = validDto();
        dto.setAvailableDate(LocalDate.now().plusDays(1));
        dto.setReadyByTime("11:00 AM today");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sellerService.createProduct(1L, dto, approvedSeller()));
        assertThat(ex.getMessage()).contains("cannot be before");
    }

    @Test
    void invalidHhmmFormatRejected() {
        ProductCreateDto dto = validDto();
        dto.setOrderWindowEnd("25:99");
        assertThrows(IllegalArgumentException.class,
                () -> sellerService.createProduct(1L, dto, approvedSeller()));
    }

    @Test
    void readyByOneDayBeforeOfferingIsRebasedInsteadOfRejected() {
        // Client-clock day-boundary skew: readyBy carries yesterday's date while
        // the offering is for tomorrow. The time-of-day must be moved onto the
        // offering date instead of failing the whole create with a 400.
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        allowFutureMenus();
        ProductCreateDto dto = validDto();
        dto.setAvailableDate(tomorrow);
        dto.setReadyByTime(LocalDateTime.of(today, LocalTime.of(12, 0)).toString());
        var result = sellerService.createProduct(1L, dto, approvedSeller());
        assertThat(result.getAvailableDate()).isEqualTo(tomorrow);
        assertThat(result.getReadyByTime())
                .isEqualTo(LocalDateTime.of(tomorrow, LocalTime.NOON).toString());
    }

    @Test
    void readyByMultiDayBeforeOfferingIsRebasedKeepingTimeOfDay() {
        LocalDate today = LocalDate.now();
        LocalDate offering = today.plusDays(2);
        allowFutureMenus();
        ProductCreateDto dto = validDto();
        dto.setAvailableDate(offering);
        dto.setReadyByTime(LocalDateTime.of(today.minusDays(1), LocalTime.of(23, 30)).toString());
        var result = sellerService.createProduct(1L, dto, approvedSeller());
        assertThat(result.getReadyByTime())
                .isEqualTo(LocalDateTime.of(offering, LocalTime.of(23, 30)).toString());
    }

    @Test
    void readyByOnTheNextDayIsAcceptedForTodayOffering() {
        // e2e marketplace-lifecycle case: offering Today, readyBy spans tomorrow.
        LocalDate today = LocalDate.now();
        allowFutureMenus();
        ProductCreateDto dto = validDto();
        dto.setAvailableDate(today);
        dto.setReadyByTime(LocalDateTime.of(today.plusDays(1), LocalTime.NOON).toString());
        var result = sellerService.createProduct(1L, dto, approvedSeller());
        assertThat(result.getReadyByTime())
                .isEqualTo(LocalDateTime.of(today.plusDays(1), LocalTime.NOON).toString());
    }
}
