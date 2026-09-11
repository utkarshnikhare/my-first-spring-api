package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.FavouriteDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.model.Favourite;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.FavouriteRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class FavouriteService {

    private final FavouriteRepository favouriteRepository;
    private final KitchenRepository kitchenRepository;

    @Autowired
    public FavouriteService(FavouriteRepository favouriteRepository, KitchenRepository kitchenRepository) {
        this.favouriteRepository = favouriteRepository;
        this.kitchenRepository = kitchenRepository;
    }

    @Transactional(readOnly = true)
    public List<FavouriteDto> getFavouriteKitchens(User buyer) {
        List<FavouriteDto> kitchens = new ArrayList<>();
        for (Favourite f : favouriteRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId())) {
            if (f.getKitchen() != null) kitchens.add(toKitchenDto(f));
        }
        return kitchens;
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
            throw new IllegalArgumentException("You can favourite up to 3 kitchens only.");
        }
        Favourite f = new Favourite();
        f.setUser(buyer);
        f.setKitchen(kitchen);
        try {
            favouriteRepository.save(f);
        } catch (DataIntegrityViolationException ex) {
            favouriteRepository.flush();
            return true;
        }
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
}
