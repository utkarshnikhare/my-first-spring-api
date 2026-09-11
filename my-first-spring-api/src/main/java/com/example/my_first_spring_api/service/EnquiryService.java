package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.EnquiryDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.model.Enquiry;
import com.example.my_first_spring_api.model.EnquiryStatus;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.EnquiryRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.PlatformSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class EnquiryService {

    private final EnquiryRepository enquiryRepository;
    private final KitchenRepository kitchenRepository;
    private final NotificationService notificationService;
    private final LedgerService ledgerService;
    private final AnalyticsService analyticsService;
    private final PlatformSettingRepository platformSettingRepository;

    @Autowired
    public EnquiryService(EnquiryRepository enquiryRepository, KitchenRepository kitchenRepository,
                          NotificationService notificationService, LedgerService ledgerService,
                          AnalyticsService analyticsService, PlatformSettingRepository platformSettingRepository) {
        this.enquiryRepository = enquiryRepository;
        this.kitchenRepository = kitchenRepository;
        this.notificationService = notificationService;
        this.ledgerService = ledgerService;
        this.analyticsService = analyticsService;
        this.platformSettingRepository = platformSettingRepository;
    }

    public EnquiryDto submit(User buyer, Long kitchenId, String message, String preferredDate, String quantity, String referenceImageUrl) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Enquiry message cannot be empty.");
        }
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        Enquiry enquiry = new Enquiry();
        enquiry.setUser(buyer);
        enquiry.setKitchen(kitchen);
        enquiry.setMessage(message.trim());
        enquiry.setStatus(EnquiryStatus.NEW);
        enquiry.setPreferredDate(preferredDate);
        enquiry.setQuantity(quantity);
        enquiry.setReferenceImageUrl(referenceImageUrl);
        enquiry = enquiryRepository.save(enquiry);

        if (kitchen.getSeller() != null) {
            notificationService.sendNewEnquiryNotification(kitchen.getSeller(), buyer.getName());
        }
        analyticsService.record(AnalyticsService.EV_ENQUIRY_SUBMITTED, buyer.getId(),
                buyer.getMobileNumber(), kitchen.getId(), "enquiry:" + enquiry.getId());
        BigDecimal leadFee = resolveLeadFee();
        ledgerService.recordEnquiryLeadFee(
                kitchen.getSeller() != null ? kitchen.getSeller().getId() : null,
                buyer.getId(), enquiry.getId(), leadFee);

        return toDto(enquiry);
    }

    @Transactional(readOnly = true)
    public List<EnquiryDto> getEnquiries(User buyer) {
        return enquiryRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnquiryDto> getSellerEnquiries(User seller) {
        return enquiryRepository.findByKitchenSellerIdOrderByCreatedAtDesc(seller.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    public EnquiryDto acknowledge(Long enquiryId, User seller) {
        Enquiry enquiry = enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new IllegalArgumentException("Enquiry not found"));
        if (!enquiry.getKitchen().getSeller().getId().equals(seller.getId())) {
            throw new IllegalArgumentException("Not authorized for this enquiry");
        }
        enquiry.setAcknowledgedAt(LocalDateTime.now());
        enquiry.setAcknowledgedBy(seller);
        if (enquiry.getStatus() == EnquiryStatus.NEW) {
            enquiry.setStatus(EnquiryStatus.CONTACTED);
        }
        enquiry = enquiryRepository.save(enquiry);
        return toDto(enquiry);
    }

    public EnquiryDto updateStatus(Long enquiryId, EnquiryStatus newStatus, User seller) {
        Enquiry enquiry = enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new IllegalArgumentException("Enquiry not found"));
        if (!enquiry.getKitchen().getSeller().getId().equals(seller.getId())) {
            throw new IllegalArgumentException("Not authorized for this enquiry");
        }
        enquiry.setStatus(newStatus);
        if (newStatus == EnquiryStatus.CONTACTED && enquiry.getAcknowledgedAt() == null) {
            enquiry.setAcknowledgedAt(LocalDateTime.now());
            enquiry.setAcknowledgedBy(seller);
        }
        enquiry = enquiryRepository.save(enquiry);
        return toDto(enquiry);
    }

    private BigDecimal resolveLeadFee() {
        return platformSettingRepository.findBySettingKey("enquiry_lead_fee")
                .map(setting -> {
                    try {
                        return new BigDecimal(setting.getSettingValue());
                    } catch (Exception e) {
                        return BigDecimal.ZERO;
                    }
                })
                .orElse(BigDecimal.ZERO);
    }

    private EnquiryDto toDto(Enquiry e) {
        EnquiryDto dto = new EnquiryDto();
        dto.setId(e.getId());
        dto.setKitchenId(e.getKitchen().getId());
        dto.setKitchenName(e.getKitchen().getDisplayName());
        dto.setKitchenImageUrl(e.getKitchen().getImageUrl());
        dto.setMessage(e.getMessage());
        dto.setStatus(e.getStatus().name());
        dto.setPreferredDate(e.getPreferredDate());
        dto.setQuantity(e.getQuantity());
        dto.setReferenceImageUrl(e.getReferenceImageUrl());
        dto.setAcknowledgedAt(e.getAcknowledgedAt());
        dto.setAcknowledgedBySellerId(e.getAcknowledgedBy() != null ? e.getAcknowledgedBy().getId() : null);
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
