package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.EnquiryDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.model.Enquiry;
import com.example.my_first_spring_api.model.EnquiryStatus;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.EnquiryRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EnquiryService {

    private final EnquiryRepository enquiryRepository;
    private final KitchenRepository kitchenRepository;

    @Autowired
    public EnquiryService(EnquiryRepository enquiryRepository, KitchenRepository kitchenRepository) {
        this.enquiryRepository = enquiryRepository;
        this.kitchenRepository = kitchenRepository;
    }

    public EnquiryDto submit(User buyer, Long kitchenId, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Enquiry message cannot be empty.");
        }
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        Enquiry enquiry = new Enquiry();
        enquiry.setUser(buyer);
        enquiry.setKitchen(kitchen);
        enquiry.setMessage(message.trim());
        enquiry.setStatus(EnquiryStatus.WAITING_FOR_RESPONSE);
        enquiry = enquiryRepository.save(enquiry);
        return toDto(enquiry);
    }

    @Transactional(readOnly = true)
    public List<EnquiryDto> getEnquiries(User buyer) {
        return enquiryRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    private EnquiryDto toDto(Enquiry e) {
        EnquiryDto dto = new EnquiryDto();
        dto.setId(e.getId());
        dto.setKitchenId(e.getKitchen().getId());
        dto.setKitchenName(e.getKitchen().getDisplayName());
        dto.setKitchenImageUrl(e.getKitchen().getImageUrl());
        dto.setMessage(e.getMessage());
        dto.setStatus(e.getStatus().name());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
