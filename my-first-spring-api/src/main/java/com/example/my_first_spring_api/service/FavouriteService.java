package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.FavouriteDto;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
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
    private final com.example.my_first_spring_api.repository.UserRepository userRepository;

    /** Business rule: a buyer may keep at most this many kitchens favourited. */
    static final int MAX_FAVOURITES = 3;

    @Autowired
    public FavouriteService(FavouriteRepository favouriteRepository, KitchenRepository kitchenRepository,
                            com.example.my_first_spring_api.repository.UserRepository userRepository) {
        this.favouriteRepository = favouriteRepository;
        this.kitchenRepository = kitchenRepository;
        this.userRepository = userRepository;
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
        if (!KitchenVisibility.isPubliclyVisible(kitchen) || !KitchenVisibility.isServiceAreaVisible(kitchen, buyer)) {
            throw new com.example.my_first_spring_api.exception.InvalidKitchenSelectionException(
                    "This kitchen is not available in your area.");
        }
        var existing = favouriteRepository.findByUserIdAndKitchenId(buyer.getId(), kitchenId);
        if (existing.isPresent()) {
            favouriteRepository.delete(existing.get());
            return false;
        }
        // Take a row lock on the buyer before counting. Two concurrent toggle requests for the
        // same buyer now serialise here, so the count-then-insert below can no longer both
        // observe a stale count and both insert (the previous TOCTOU race).
        userRepository.findByIdForUpdate(buyer.getId())
                .orElseThrow(() -> new BuyerNotAuthenticatedException("Buyer not found."));

        // Reserve the slot in the same transaction that inserts the row. The previous
        // count-then-insert check was a TOCTOU race: two concurrent requests could both
        // observe count == 2 and both insert, exceeding the limit.
        long total = favouriteRepository.countByUserId(buyer.getId());
        if (total >= MAX_FAVOURITES) {
            throw new IllegalArgumentException("You can favourite up to " + MAX_FAVOURITES + " kitchens only.");
        }
        Favourite f = new Favourite();
        f.setUser(buyer);
        f.setKitchen(kitchen);
        // No try/catch here. A DataIntegrityViolationException cannot be recovered from
        // by re-flushing the same broken persistence context - doing so re-throws and
        // silently swallowed the only diagnostic. The (user_id, kitchen_id) unique
        // constraint is the authority: if a concurrent request inserted the same favourite
        // first, the constraint violation propagates and the transaction rolls back,
        // which is the correct outcome for a double-submit.
        favouriteRepository.saveAndFlush(f);
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
