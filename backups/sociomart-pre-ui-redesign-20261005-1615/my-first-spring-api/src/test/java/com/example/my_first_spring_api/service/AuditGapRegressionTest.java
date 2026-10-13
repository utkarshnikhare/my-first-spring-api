package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.exception.InvalidKitchenSelectionException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuditGapRegressionTest {
    @Mock EnquiryRepository enquiryRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock NotificationService notificationService;
    @Mock LedgerService ledgerService;
    @Mock AnalyticsService analyticsService;
    @Mock PlatformSettingRepository platformSettingRepository;
    @Mock FavouriteRepository favouriteRepository;
    @InjectMocks EnquiryService enquiryService;
    @InjectMocks FavouriteService favouriteService;
    private User buyer;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        buyer = new User("Buyer", "9876500010", "A-1", UserRole.BUYER);
        buyer.setId(20L);
        buyer.setSociety("My Society");
        User seller = new User("Seller", "9100000010", "S-1", UserRole.SELLER);
        seller.setId(10L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        kitchen = new Kitchen("audit-kitchen", "Audit Kitchen", "", null, seller);
        kitchen.setId(1L);
    }

    @Test
    void enquiryRejectsPausedKitchen() {
        kitchen.setAvailableToday(false);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        assertThatThrownBy(() -> enquiryService.submit(buyer, 1L, "Need a cake", null, null, null))
                .isInstanceOf(InvalidKitchenSelectionException.class);
        verify(enquiryRepository, never()).save(any());
        verifyNoInteractions(ledgerService);
    }

    @Test
    void enquiryRejectsKitchenOutsideBuyerServiceArea() {
        kitchen.setServiceAreas("Other Society");
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        assertThatThrownBy(() -> enquiryService.submit(buyer, 1L, "Need a cake", null, null, null))
                .isInstanceOf(InvalidKitchenSelectionException.class);
        verify(enquiryRepository, never()).save(any());
    }

    @Test
    void favouriteRejectsKitchenOutsideBuyerServiceArea() {
        kitchen.setServiceAreas("Other Society");
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        assertThatThrownBy(() -> favouriteService.toggleKitchen(buyer, 1L))
                .isInstanceOf(InvalidKitchenSelectionException.class);
        verify(favouriteRepository, never()).save(any());
    }
}