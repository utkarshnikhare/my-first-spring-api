package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SellerService {

    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;

    @Autowired
    public SellerService(KitchenRepository kitchenRepository,
                         ProductRepository productRepository,
                         OrderService orderService) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.orderService = orderService;
    }

    public KitchenDto createKitchen(KitchenCreateDto dto, User seller) {
        String slug = dto.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (kitchenRepository.findByName(slug).isPresent()) {
            throw new IllegalArgumentException("A kitchen with this URL name already exists.");
        }
        Kitchen kitchen = new Kitchen(slug, dto.getDisplayName(), dto.getDescription(), dto.getImageUrl(), seller);
        kitchen.setShortDescription(dto.getShortDescription());
        kitchen.setSociety(dto.getSociety());
        kitchen.setBuilding(dto.getBuilding());
        kitchen.setWhatsappLink(dto.getWhatsappLink());
        kitchen.setInstagramLink(dto.getInstagramLink());
        kitchen.setUpiId(dto.getUpiId());
        kitchen.setAvailableToday(dto.getAvailableToday() != null ? dto.getAvailableToday() : true);
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    public KitchenDto updateKitchen(Long kitchenId, KitchenUpdateDto dto, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank()) kitchen.setDisplayName(dto.getDisplayName());
        if (dto.getDescription() != null) kitchen.setDescription(dto.getDescription());
        if (dto.getShortDescription() != null) kitchen.setShortDescription(dto.getShortDescription());
        if (dto.getImageUrl() != null) kitchen.setImageUrl(dto.getImageUrl());
        if (dto.getSociety() != null) kitchen.setSociety(dto.getSociety());
        if (dto.getBuilding() != null) kitchen.setBuilding(dto.getBuilding());
        if (dto.getWhatsappLink() != null) kitchen.setWhatsappLink(dto.getWhatsappLink());
        if (dto.getInstagramLink() != null) kitchen.setInstagramLink(dto.getInstagramLink());
        if (dto.getUpiId() != null) kitchen.setUpiId(dto.getUpiId());
        if (dto.getAvailableToday() != null) kitchen.setAvailableToday(dto.getAvailableToday());
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    @Transactional(readOnly = true)
    public KitchenDto getMyKitchen(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        return kitchens.isEmpty() ? null : toKitchenDto(kitchens.get(0));
    }

    public ProductDto createProduct(Long kitchenId, ProductCreateDto dto, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        Product product = new Product(kitchen, dto.getName(), dto.getDescription(), dto.getPrice(), dto.getImageUrl());
        product.setPriceUnit(dto.getPriceUnit());
        product.setAvailableToday(dto.getAvailableToday() != null ? dto.getAvailableToday() : true);
        product.setAvailableDate(dto.getAvailableDate());
        product.setOrderWindowStart(dto.getOrderWindowStart());
        product.setOrderWindowEnd(dto.getOrderWindowEnd());
        product.setMaxQuantity(dto.getMaxQuantity());
        product.setRemainingQuantity(dto.getMaxQuantity());
        product.setIsPreorder(dto.getIsPreorder() != null ? dto.getIsPreorder() : false);
        return toProductDto(productRepository.save(product));
    }

    public ProductDto updateProduct(Long productId, ProductCreateDto dto, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        getOwnedKitchen(product.getKitchen().getId(), seller);
        if (dto.getName() != null && !dto.getName().isBlank()) product.setName(dto.getName());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getPrice() != null) product.setPrice(dto.getPrice());
        if (dto.getPriceUnit() != null) product.setPriceUnit(dto.getPriceUnit());
        if (dto.getImageUrl() != null) product.setImageUrl(dto.getImageUrl());
        if (dto.getAvailableToday() != null) {
            product.setAvailableToday(dto.getAvailableToday());
            if (dto.getAvailableToday() && product.getRemainingQuantity() == null && product.getMaxQuantity() != null) {
                product.setRemainingQuantity(product.getMaxQuantity());
            }
        }
        if (dto.getRemainingQuantity() != null) product.setRemainingQuantity(dto.getRemainingQuantity());
        if (dto.getAvailableDate() != null) product.setAvailableDate(dto.getAvailableDate());
        if (dto.getOrderWindowStart() != null) product.setOrderWindowStart(dto.getOrderWindowStart());
        if (dto.getOrderWindowEnd() != null) product.setOrderWindowEnd(dto.getOrderWindowEnd());
        if (dto.getMaxQuantity() != null) product.setMaxQuantity(dto.getMaxQuantity());
        if (dto.getIsPreorder() != null) product.setIsPreorder(dto.getIsPreorder());
        return toProductDto(productRepository.save(product));
    }

    public void deleteProduct(Long productId, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        getOwnedKitchen(product.getKitchen().getId(), seller);
        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getMyProducts(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        if (kitchens.isEmpty()) return List.of();
        return kitchens.stream()
                .flatMap(k -> productRepository.findByKitchen(k).stream())
                .map(this::toProductDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getMyOrders(User seller) {
        return orderService.getSellerOrders(seller);
    }

    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus, User seller) {
        return orderService.updateOrderStatus(orderId, newStatus, seller);
    }

    private Kitchen getOwnedKitchen(Long kitchenId, User seller) {
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        if (!kitchen.getSeller().getId().equals(seller.getId())) throw new SellerNotAuthorizedException("You do not own this kitchen");
        return kitchen;
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
        return dto;
    }
}
