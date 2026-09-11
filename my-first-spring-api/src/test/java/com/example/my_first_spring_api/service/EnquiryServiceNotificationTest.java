package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EnquiryServiceNotificationTest {

    @Mock EnquiryRepository enquiryRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock NotificationService notificationService;
    @Mock LedgerService ledgerService;
    @Mock AnalyticsService analyticsService;
    @Mock PlatformSettingRepository platformSettingRepository;

    @InjectMocks EnquiryService enquiryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void submitSendsNotificationAndRecordsLedger() {
        User seller = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        seller.setId(10L);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        User buyer = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        buyer.setId(20L);

        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(inv -> {
            Enquiry e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });
        when(platformSettingRepository.findBySettingKey("enquiry_lead_fee"))
                .thenReturn(Optional.of(new PlatformSetting("enquiry_lead_fee", "25")));

        var dto = enquiryService.submit(buyer, 1L, "Need a cake", "2026-09-20", "2 kg", null);

        assertThat(dto.getId()).isEqualTo(100L);
        verify(notificationService, times(1)).sendNewEnquiryNotification(eq(seller), eq(buyer.getName()));
        verify(analyticsService, times(1)).record(eq(AnalyticsService.EV_ENQUIRY_SUBMITTED), eq(buyer.getId()),
                eq(buyer.getMobileNumber()), eq(kitchen.getId()), any(String.class));
        verify(ledgerService, times(1)).recordEnquiryLeadFee(eq(10L), eq(20L), eq(100L), eq(new BigDecimal("25")));
    }

    @Test
    void submitUsesZeroLeadFeeWhenNotConfigured() {
        User seller = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        seller.setId(10L);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        User buyer = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        buyer.setId(20L);

        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(inv -> {
            Enquiry e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });
        when(platformSettingRepository.findBySettingKey("enquiry_lead_fee"))
                .thenReturn(Optional.empty());

        enquiryService.submit(buyer, 1L, "Need a cake", null, null, null);

        verify(ledgerService, times(1)).recordEnquiryLeadFee(eq(10L), eq(20L), eq(101L), eq(BigDecimal.ZERO));
    }
}
