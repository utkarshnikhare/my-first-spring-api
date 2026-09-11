package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.MarketplaceDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MarketplaceService {

    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final AnalyticsService analyticsService;

    @Autowired
    public MarketplaceService(KitchenRepository kitchenRepository, ProductRepository productRepository,
                              AnalyticsService analyticsService) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.analyticsService = analyticsService;
    }

    public MarketplaceDto getMarketplaceHome(User buyer) {
        analyticsService.record(AnalyticsService.EV_MARKETPLACE_VIEW, null, null, null, null);
        List<Kitchen> visibleKitchens = kitchenRepository.findAll().stream()
                .filter(KitchenVisibility::isPubliclyVisible)
                .filter(k -> isServiceAreaVisible(k, buyer))
                .collect(Collectors.toList());

        List<ProductDto> availableToday = productRepository.findByAvailableTodayTrueOrderByCreatedAtDesc().stream()
                .filter(p -> p.getKitchen() == null || (KitchenVisibility.isPubliclyVisible(p.getKitchen()) && isServiceAreaVisible(p.getKitchen(), buyer)))
                .map(this::toProductDto).collect(Collectors.toList());

        List<ProductDto> newProducts = availableToday;
        List<ProductDto> popularProducts = availableToday.stream()
                .sorted((p1, p2) -> Double.compare(p2.getRating(), p1.getRating()))
                .collect(Collectors.toList());

        return new MarketplaceDto(
                visibleKitchens.stream().map(this::toKitchenDto).collect(Collectors.toList()),
                popularProducts, newProducts, availableToday);
    }

    public List<KitchenDto> getAllActiveKitchens(User buyer) {
        return kitchenRepository.findAll().stream()
                .filter(KitchenVisibility::isPubliclyVisible)
                .filter(k -> isServiceAreaVisible(k, buyer))
                .map(this::toKitchenDto)
                .collect(Collectors.toList());
    }

    public List<ProductDto> getAllAvailableItems(User buyer) {
        return productRepository.findByAvailableTodayTrueOrderByCreatedAtDesc().stream()
                .filter(p -> p.getKitchen() != null && KitchenVisibility.isPubliclyVisible(p.getKitchen()) && isServiceAreaVisible(p.getKitchen(), buyer))
                .map(this::toProductDto)
                .collect(Collectors.toList());
    }

    private static boolean isServiceAreaVisible(Kitchen kitchen, User buyer) {
        String areas = kitchen.getServiceAreas();
        if (areas == null || areas.isBlank()) {
            String society = kitchen.getSociety();
            if (society == null || society.isBlank()) return true;
            if (buyer == null || buyer.getSociety() == null) return true;
            return society.equalsIgnoreCase(buyer.getSociety());
        }
        if (buyer == null || buyer.getSociety() == null) return true;
        String[] parts = areas.split(",");
        for (String part : parts) {
            if (part.trim().equalsIgnoreCase(buyer.getSociety())) return true;
        }
        return false;
    }

    private KitchenDto toKitchenDto(Kitchen kitchen) {
        return new KitchenDto(kitchen.getId(), kitchen.getName(), kitchen.getDisplayName(),
                kitchen.getDescription(), kitchen.getImageUrl(), kitchen.getRating(),
                kitchen.getAvailableToday(), kitchen.getSeller() != null ? kitchen.getSeller().getId() : null);
    }

    private ProductDto toProductDto(Product product) {
        Kitchen kitchen = product.getKitchen();
        ProductDto dto = new ProductDto(product.getId(), kitchen != null ? kitchen.getId() : null,
                kitchen != null ? kitchen.getDisplayName() : null, product.getName(),
                product.getDescription(), product.getPrice(), product.getImageUrl(),
                product.getAvailableToday(), product.getRating());
        dto.setPriceUnit(product.getPriceUnit());
        dto.setAvailableDate(product.getAvailableDate());
        dto.setOrderWindowStart(product.getOrderWindowStart());
        dto.setOrderWindowEnd(product.getOrderWindowEnd());
        dto.setMaxQuantity(product.getMaxQuantity());
        dto.setRemainingQuantity(product.getRemainingQuantity());
        dto.setIsPreorder(product.getIsPreorder());
        dto.setKitchenSlug(kitchen != null ? kitchen.getName() : null);
        dto.setCategory(product.getCategory());
        dto.setCutoffTime(product.getCutoffTime());
        dto.setReadyByTime(product.getReadyByTime());
        dto.setPreorderType(product.getPreorderType() != null ? product.getPreorderType().name() : null);
        dto.setAvailableUntilDate(product.getAvailableUntilDate());
        dto.setTimeSlots(product.getTimeSlots());
        dto.setBookedQuantity(product.getBookedQuantity());
        dto.setSoldOut(product.isSoldOut());
        return dto;
    }
}
