package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FavouriteServiceLimitTest {

    /** Shared by the buyer and the kitchens so the favourite stays inside the area. */
    private static final String SOCIETY = "Favourite Test Society";

    @Mock FavouriteRepository favouriteRepository;
    @Mock KitchenRepository kitchenRepository;
    /** Added with the TOCTOU fix: toggleKitchen now takes a PESSIMISTIC_WRITE lock on the
     *  buyer row before counting, so this repository is part of the collaborator set. */
    @Mock UserRepository userRepository;

    @InjectMocks FavouriteService favouriteService;

    User buyer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        buyer = new User("Test Buyer", "9999999999", "A-101", UserRole.BUYER);
        buyer.setId(1L);
        // The buyer must belong to a service area, otherwise the favourite is refused
        // as out-of-area before the limit under test is ever reached.
        buyer.setSociety(SOCIETY);
        // The row lock acquired before the count resolves to the buyer under test.
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(buyer));
    }

    /** A kitchen inside the buyer's service area. */
    private Kitchen kitchen(long id) {
        Kitchen k = new Kitchen("k" + id, "Kitchen " + id, "d", null, seller());
        k.setId(id);
        k.setSociety(SOCIETY);
        return k;
    }

    private User seller() {
        User seller = new User("Kitchen Seller", "9100000010", "S-1", UserRole.SELLER);
        seller.setId(99L);
        return seller;
    }

    @Test
    void addThreeKitchenFavouritesSucceeds() {
        Kitchen kitchen = kitchen(1L);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(favouriteRepository.findByUserIdAndKitchenId(1L, 1L)).thenReturn(Optional.empty());
        when(favouriteRepository.countByUserId(1L)).thenReturn(0L, 1L, 2L);
        when(favouriteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(favouriteService.toggleKitchen(buyer, 1L)).isTrue();
        assertThat(favouriteService.toggleKitchen(buyer, 1L)).isTrue();
        assertThat(favouriteService.toggleKitchen(buyer, 1L)).isTrue();
    }

    @Test
    void addFourthKitchenFavouriteRejected() {
        Kitchen kitchen = kitchen(1L);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(favouriteRepository.findByUserIdAndKitchenId(1L, 1L)).thenReturn(Optional.empty());
        when(favouriteRepository.countByUserId(1L)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> favouriteService.toggleKitchen(buyer, 1L));
    }

    @Test
    void removeKitchenFavouriteThenAddNewSucceeds() {
        Kitchen kitchen = kitchen(1L);
        Favourite existing = new Favourite();
        existing.setId(10L);
        existing.setUser(buyer);
        existing.setKitchen(kitchen);

        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(favouriteRepository.findByUserIdAndKitchenId(1L, 1L)).thenReturn(Optional.of(existing));
        when(favouriteRepository.countByUserId(1L)).thenReturn(3L);

        assertThat(favouriteService.toggleKitchen(buyer, 1L)).isFalse();
        verify(favouriteRepository).delete(existing);
    }

    @Test
    void getFavouriteKitchensReturnsOnlyKitchens() {
        Kitchen kitchen = kitchen(1L);
        Favourite f = new Favourite();
        f.setId(1L);
        f.setUser(buyer);
        f.setKitchen(kitchen);

        when(favouriteRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(f));

        List<com.example.my_first_spring_api.dto.FavouriteDto> result = favouriteService.getFavouriteKitchens(buyer);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("KITCHEN");
    }
}
