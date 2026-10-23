package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.LedgerEvent;
import com.example.my_first_spring_api.repository.LedgerEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LedgerService {

    private final LedgerEventRepository ledgerEventRepository;

    @Autowired
    public LedgerService(LedgerEventRepository ledgerEventRepository) {
        this.ledgerEventRepository = ledgerEventRepository;
    }

    public void recordEnquiryLeadFee(Long sellerId, Long buyerId, Long enquiryId, BigDecimal amount) {
        LedgerEvent event = new LedgerEvent();
        event.setEventType("QUALIFIED_ENQUIRY_SUBMITTED");
        event.setSellerId(sellerId);
        event.setBuyerId(buyerId);
        event.setEnquiryId(enquiryId);
        event.setAmount(amount != null ? amount : BigDecimal.ZERO);
        event.setCurrency("INR");
        event.setMetadata("{\"source\":\"enquiry\"}");
        ledgerEventRepository.save(event);
    }
}
