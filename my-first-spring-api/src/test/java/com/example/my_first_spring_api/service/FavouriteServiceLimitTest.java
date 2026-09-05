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

    @Mock FavouriteRepository favouriteRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock ProductRepository productRepository;

    @InjectMocks FavouriteService favouriteService;

    User buyer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        buyer = new User("Test Buyer", "9999999999", "A-101", UserRole.BUYER);
        buyer.setId(1L);
    }

    @Test
    void addThreeFavouritesSucceeds() {
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, null);
        kitchen.setId(1L);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(favouriteRepository.findByUserIdAndKitchenId(1L, 1L)).thenReturn(Optional.empty());
        when(favouriteRepository.countByUserId(1L)).thenReturn(0L, 1L, 2L);
        when(favouriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(favouriteService.toggleKitchen(buyer, 1L)).isTrue();
        assertThat(favouriteService.toggleKitchen(buyer, 1L)).isTrue();
        assertThat(favouriteService.toggleKitchen(buyer, 1L)).isTrue();
    }

    @Test
    void addFourthFavouriteRejected() {
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, null);
        kitchen.setId(1L);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(favouriteRepository.findByUserIdAndKitchenId(1L, 1L)).thenReturn(Optional.empty());
        when(favouriteRepository.countByUserId(1L)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> favouriteService.toggleKitchen(buyer, 1L));
    }

    @Test
    void removeFavouriteThenAddNewSucceeds() {
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, null);
        kitchen.setId(1L);
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
    void productFavouritsEnforcedSeparatelyButCountedTogether() {
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, null);
        kitchen.setId(1L);
        Product product = new Product(kitchen, "Poha", "desc", java.math.BigDecimal.valueOf(40), null);
        product.setId(1L);

        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(favouriteRepository.findByUserIdAndKitchenId(1L, 1L)).thenReturn(Optional.empty());
        when(favouriteRepository.findByUserIdAndProductId(1L, 1L)).thenReturn(Optional.empty());
        when(favouriteRepository.countByUserId(1L)).thenReturn(0L, 1L, 2L);
        when(favouriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(favouriteService.toggleKitchen(buyer, 1L)).isTrue();
        assertThat(favouriteService.toggleProduct(buyer, 1L)).isTrue();
        assertThat(favouriteService.toggleProduct(buyer, 1L)).isTrue();

        when(favouriteRepository.countByUserId(1L)).thenReturn(3L);
        assertThrows(IllegalArgumentException.class, () -> favouriteService.toggleProduct(buyer, 1L));
    }
}
