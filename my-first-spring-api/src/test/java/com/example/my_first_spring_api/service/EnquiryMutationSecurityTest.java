package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EnquiryMutationSecurityTest {
    @Mock EnquiryRepository enquiryRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock NotificationService notificationService;
    @Mock LedgerService ledgerService;
    @Mock AnalyticsService analyticsService;
    @Mock PlatformSettingRepository platformSettingRepository;
    private EnquiryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new EnquiryService(enquiryRepository, kitchenRepository, notificationService,
                ledgerService, analyticsService, platformSettingRepository);
    }

    @Test
    void acknowledgeLocksAndRejectsAnotherSeller() {
        User owner = seller(10L);
        User attacker = seller(20L);
        Enquiry enquiry = enquiry(owner, 1L);
        when(enquiryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enquiry));
        assertThatThrownBy(() -> service.acknowledge(1L, attacker))
                .isInstanceOf(SellerNotAuthorizedException.class);
        verify(enquiryRepository, never()).save(any());
    }

    @Test
    void statusLocksAndRejectsAnotherSeller() {
        User owner = seller(10L);
        User attacker = seller(20L);
        Enquiry enquiry = enquiry(owner, 1L);
        when(enquiryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enquiry));
        assertThatThrownBy(() -> service.updateStatus(1L, EnquiryStatus.CLOSED, attacker))
                .isInstanceOf(SellerNotAuthorizedException.class);
        verify(enquiryRepository, never()).save(any());
    }

    private User seller(Long id) {
        User seller = new User("Seller", "9100000000" + id, "S-1", UserRole.SELLER);
        seller.setId(id);
        return seller;
    }

    private Enquiry enquiry(User owner, Long id) {
        Kitchen kitchen = new Kitchen("k", "Kitchen", "", null, owner);
        kitchen.setId(1L);
        Enquiry enquiry = new Enquiry();
        enquiry.setId(id);
        enquiry.setKitchen(kitchen);
        return enquiry;
    }
}
