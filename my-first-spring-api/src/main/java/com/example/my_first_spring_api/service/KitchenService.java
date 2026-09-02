package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.SearchResultDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class KitchenService {

    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final AnalyticsService analyticsService;

    @Autowired
    public KitchenService(KitchenRepository kitchenRepository, ProductRepository productRepository,
                          AnalyticsService analyticsService) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.analyticsService = analyticsService;
    }

    public KitchenDetailDto getKitchenByName(String name) {
        Kitchen kitchen = kitchenRepository.findByName(name)
                .orElseThrow(() -> new KitchenNotFoundException(name));
        if (!KitchenVisibility.isPubliclyVisible(kitchen)) {
            throw new KitchenNotFoundException(name);
        }
        analyticsService.record(AnalyticsService.EV_MENU_VIEW, null, null,
                kitchen.getId(), kitchen.getDisplayName());
        KitchenDto kitchenDto = toKitchenDto(kitchen);
        List<ProductDto> products = productRepository
                .findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(kitchen).stream()
                .map(this::toProductDto).collect(Collectors.toList());
        List<ProductDto> preorderProducts = products.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsPreorder())).collect(Collectors.toList());
        List<ProductDto> regularProducts = products.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsPreorder())).collect(Collectors.toList());
        KitchenDetailDto detailDto = new KitchenDetailDto(kitchenDto, regularProducts);
        detailDto.setPreorderProducts(preorderProducts);
        return detailDto;
    }

    public KitchenDto getKitchenById(Long id) {
        Kitchen kitchen = kitchenRepository.findById(id)
                .orElseThrow(() -> new KitchenNotFoundException(id));
        return toKitchenDto(kitchen);
    }

    /**
     * Public kitchen storefront (Screen 4): identity + offerings split strictly
     * into "Available Today" and "Pre-order" sections.
     */
    public KitchenDetailDto getKitchenDetailById(Long id) {
        Kitchen kitchen = kitchenRepository.findById(id)
                .orElseThrow(() -> new KitchenNotFoundException(id));
        if (!KitchenVisibility.isPubliclyVisible(kitchen)) {
            throw new KitchenNotFoundException(id);
        }
        KitchenDto kitchenDto = toKitchenDto(kitchen);
        List<ProductDto> all = productRepository.findByKitchen(kitchen).stream()
                .map(this::toProductDto).collect(Collectors.toList());
        List<ProductDto> preorder = all.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsPreorder()))
                .collect(Collectors.toList());
        List<ProductDto> today = all.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsPreorder()) && Boolean.TRUE.equals(p.getAvailableToday()))
                .collect(Collectors.toList());
        KitchenDetailDto dto = new KitchenDetailDto(kitchenDto, today);
        dto.setPreorderProducts(preorder);
        return dto;
    }

    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new com.example.my_first_spring_api.exception.ProductNotFoundException(id));
        if (product.getKitchen() != null && !KitchenVisibility.isPubliclyVisible(product.getKitchen())) {
            throw new com.example.my_first_spring_api.exception.ProductNotFoundException(id);
        }
        return toProductDto(product);
    }

    public List<ProductDto> getProductsByKitchenName(String kitchenName) {
        Kitchen kitchen = kitchenRepository.findByName(kitchenName)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenName));
        if (!KitchenVisibility.isPubliclyVisible(kitchen)) {
            throw new KitchenNotFoundException(kitchenName);
        }
        return productRepository.findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(kitchen).stream()
                .map(this::toProductDto).collect(Collectors.toList());
    }

    public SearchResultDto search(String query) {
        List<ProductDto> products = productRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::toProductDto)
                .filter(p -> p.getKitchenId() == null
                        || kitchenRepository.findById(p.getKitchenId())
                            .map(KitchenVisibility::isPubliclyVisible)
                            .orElse(false))
                .collect(Collectors.toList());

        Map<Long, KitchenDto> kitchens = new LinkedHashMap<>();
        kitchenRepository.findAll().stream()
                .filter(KitchenVisibility::isPubliclyVisible)
                .filter(k -> k.getDisplayName().toLowerCase().contains(query.toLowerCase())
                        || k.getName().toLowerCase().contains(query.toLowerCase()))
                .forEach(k -> kitchens.put(k.getId(), toKitchenDto(k)));
        for (ProductDto product : products) {
            if (product.getKitchenId() != null && !kitchens.containsKey(product.getKitchenId())) {
                kitchenRepository.findById(product.getKitchenId())
                        .filter(KitchenVisibility::isPubliclyVisible)
                        .ifPresent(k -> kitchens.put(k.getId(), toKitchenDto(k)));
            }
        }
        return new SearchResultDto(products, new ArrayList<>(kitchens.values()));
    }

    private KitchenDto toKitchenDto(Kitchen kitchen) {
        KitchenDto dto = new KitchenDto(kitchen.getId(), kitchen.getName(), kitchen.getDisplayName(),
                kitchen.getDescription(), kitchen.getImageUrl(), kitchen.getRating(),
                kitchen.getAvailableToday(), kitchen.getSeller() != null ? kitchen.getSeller().getId() : null);
        dto.setShortDescription(kitchen.getShortDescription());
        dto.setSociety(kitchen.getSociety());
        dto.setBuilding(kitchen.getBuilding());
        dto.setWhatsappLink(kitchen.getWhatsappLink());
        dto.setInstagramLink(kitchen.getInstagramLink());
        dto.setUpiId(kitchen.getUpiId());
        dto.setOrderDeadline(kitchen.getOrderDeadline());
        return dto;
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
