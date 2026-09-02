package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.MarketplaceDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public MarketplaceDto getMarketplaceHome() {
        analyticsService.record(AnalyticsService.EV_MARKETPLACE_VIEW, null, null, null, null);
        List<KitchenDto> kitchens = kitchenRepository.findAll().stream()
                .filter(KitchenVisibility::isPubliclyVisible)
                .map(this::toKitchenDto).collect(Collectors.toList());

        List<ProductDto> availableToday = productRepository.findByAvailableTodayTrueOrderByCreatedAtDesc().stream()
                .filter(p -> p.getKitchen() == null || KitchenVisibility.isPubliclyVisible(p.getKitchen()))
                .map(this::toProductDto).collect(Collectors.toList());

        List<ProductDto> newProducts = availableToday;
        List<ProductDto> popularProducts = availableToday.stream()
                .sorted((p1, p2) -> Double.compare(p2.getRating(), p1.getRating()))
                .collect(Collectors.toList());

        return new MarketplaceDto(kitchens, popularProducts, newProducts, availableToday);
    }

    /** Global browse: every publicly visible kitchen. */
    public List<KitchenDto> getAllActiveKitchens() {
        return kitchenRepository.findAll().stream()
                .filter(KitchenVisibility::isPubliclyVisible)
                .map(this::toKitchenDto)
                .collect(Collectors.toList());
    }

    /** Global browse: every available menu item across all visible kitchens. */
    public List<ProductDto> getAllAvailableItems() {
        return productRepository.findByAvailableTodayTrueOrderByCreatedAtDesc().stream()
                .filter(p -> p.getKitchen() != null && KitchenVisibility.isPubliclyVisible(p.getKitchen()))
                .map(this::toProductDto)
                .collect(Collectors.toList());
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
        dto.setCategory(product.getCategory() != null ? product.getCategory().name() : null);
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
