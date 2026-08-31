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

    @Autowired
    public KitchenService(KitchenRepository kitchenRepository, ProductRepository productRepository) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
    }

    public KitchenDetailDto getKitchenByName(String name) {
        Kitchen kitchen = kitchenRepository.findByName(name)
                .orElseThrow(() -> new KitchenNotFoundException(name));
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

    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new com.example.my_first_spring_api.exception.ProductNotFoundException(id));
        return toProductDto(product);
    }

    public List<ProductDto> getProductsByKitchenName(String kitchenName) {
        Kitchen kitchen = kitchenRepository.findByName(kitchenName)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenName));
        return productRepository.findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(kitchen).stream()
                .map(this::toProductDto).collect(Collectors.toList());
    }

    public SearchResultDto search(String query) {
        List<ProductDto> products = productRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::toProductDto).collect(Collectors.toList());

        Map<Long, KitchenDto> kitchens = new LinkedHashMap<>();
        kitchenRepository.findAll().stream()
                .filter(k -> k.getDisplayName().toLowerCase().contains(query.toLowerCase())
                        || k.getName().toLowerCase().contains(query.toLowerCase()))
                .forEach(k -> kitchens.put(k.getId(), toKitchenDto(k)));
        for (ProductDto product : products) {
            if (product.getKitchenId() != null && !kitchens.containsKey(product.getKitchenId())) {
                kitchenRepository.findById(product.getKitchenId())
                        .ifPresent(k -> kitchens.put(k.getId(), toKitchenDto(k)));
            }
        }
        return new SearchResultDto(products, new ArrayList<>(kitchens.values()));
    }

    private KitchenDto toKitchenDto(Kitchen kitchen) {
        return new KitchenDto(kitchen.getId(), kitchen.getName(), kitchen.getDisplayName(),
                kitchen.getDescription(), kitchen.getImageUrl(), kitchen.getRating(),
                kitchen.getAvailableToday(), kitchen.getSeller() != null ? kitchen.getSeller().getId() : null);
    }

    private ProductDto toProductDto(Product product) {
        Kitchen kitchen = product.getKitchen();
        return new ProductDto(product.getId(), kitchen != null ? kitchen.getId() : null,
                kitchen != null ? kitchen.getDisplayName() : null, product.getName(),
                product.getDescription(), product.getPrice(), product.getImageUrl(),
                product.getAvailableToday(), product.getRating());
    }
}
