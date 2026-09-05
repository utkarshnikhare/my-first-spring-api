package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.FavouriteDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.model.Favourite;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.FavouriteRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class FavouriteService {

    private final FavouriteRepository favouriteRepository;
    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;

    @Autowired
    public FavouriteService(FavouriteRepository favouriteRepository, KitchenRepository kitchenRepository,
                            ProductRepository productRepository) {
        this.favouriteRepository = favouriteRepository;
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, List<FavouriteDto>> getFavourites(User buyer) {
        List<FavouriteDto> kitchens = new ArrayList<>();
        List<FavouriteDto> food = new ArrayList<>();
        for (Favourite f : favouriteRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId())) {
            if (f.getKitchen() != null) kitchens.add(toKitchenDto(f));
            else if (f.getProduct() != null) food.add(toProductDto(f));
        }
        Map<String, List<FavouriteDto>> result = new HashMap<>();
        result.put("kitchens", kitchens);
        result.put("food", food);
        return result;
    }

    public boolean toggleKitchen(User buyer, Long kitchenId) {
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        var existing = favouriteRepository.findByUserIdAndKitchenId(buyer.getId(), kitchenId);
        if (existing.isPresent()) {
            favouriteRepository.delete(existing.get());
            return false;
        }
        long total = favouriteRepository.countByUserId(buyer.getId());
        if (total >= 3) {
            throw new IllegalArgumentException("You can favourite up to 3 items only.");
        }
        Favourite f = new Favourite();
        f.setUser(buyer);
        f.setKitchen(kitchen);
        favouriteRepository.save(f);
        return true;
    }

    public boolean toggleProduct(User buyer, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        var existing = favouriteRepository.findByUserIdAndProductId(buyer.getId(), productId);
        if (existing.isPresent()) {
            favouriteRepository.delete(existing.get());
            return false;
        }
        long total = favouriteRepository.countByUserId(buyer.getId());
        if (total >= 3) {
            throw new IllegalArgumentException("You can favourite up to 3 items only.");
        }
        Favourite f = new Favourite();
        f.setUser(buyer);
        f.setProduct(product);
        favouriteRepository.save(f);
        return true;
    }

    private FavouriteDto toKitchenDto(Favourite f) {
        FavouriteDto dto = new FavouriteDto();
        dto.setId(f.getId());
        dto.setType("KITCHEN");
        dto.setKitchenId(f.getKitchen().getId());
        dto.setName(f.getKitchen().getDisplayName());
        dto.setImageUrl(f.getKitchen().getImageUrl());
        dto.setSubtitle(f.getKitchen().getShortDescription());
        return dto;
    }

    private FavouriteDto toProductDto(Favourite f) {
        FavouriteDto dto = new FavouriteDto();
        dto.setId(f.getId());
        dto.setType("FOOD");
        dto.setProductId(f.getProduct().getId());
        dto.setKitchenId(f.getProduct().getKitchen().getId());
        dto.setName(f.getProduct().getName());
        dto.setImageUrl(f.getProduct().getImageUrl());
        dto.setSubtitle(f.getProduct().getDescription());
        dto.setPrice(f.getProduct().getPrice());
        dto.setKitchenName(f.getProduct().getKitchen().getDisplayName());
        return dto;
    }
}
