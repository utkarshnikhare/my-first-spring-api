package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerType;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HomemadeProductsTest {

    @Mock KitchenRepository kitchenRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;

    @InjectMocks DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private User seller(String mobile) {
        User u = new User("Seller", mobile, "A-101", UserRole.SELLER);
        u.setId(10L);
        u.setSellerApprovalStatus(com.example.my_first_spring_api.model.SellerApprovalStatus.APPROVED);
        return u;
    }

    private Kitchen homemadeKitchen() {
        Kitchen k = new Kitchen("meena-cakes", "Meena's Cakes", "Homemade Cakes", "https://example.com/meena.jpg", seller("9100000016"));
        k.setId(15L);
        k.setSellerType(SellerType.HOMEMADE_PRODUCTS);
        k.setAvailableToday(true);
        k.setServiceAreas("Lohegaon");
        return k;
    }

    @Test
    void discoveryReturnsHomemadeStoresOnly() {
        Kitchen k = homemadeKitchen();
        when(kitchenRepository.findAll()).thenReturn(List.of(k));
        when(productRepository.findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(k)).thenReturn(List.of());

        User buyer = new User("Buyer", "9876543210", "Lohegaon", UserRole.BUYER);
        buyer.setId(20L);
        buyer.setSociety("Lohegaon");

        var stores = discoveryService.getHomemadeStores(buyer);
        assertThat(stores).hasSize(1);
        assertThat(stores.get(0).getDisplayName()).isEqualTo("Meena's Cakes");
    }

    @Test
    void discoveryFiltersByServiceArea() {
        Kitchen k = homemadeKitchen();
        when(kitchenRepository.findAll()).thenReturn(List.of(k));
        when(productRepository.findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(k)).thenReturn(List.of());

        User buyer = new User("Buyer", "9876543210", "Unknown", UserRole.BUYER);
        buyer.setId(20L);
        buyer.setSociety("Unknown");

        var stores = discoveryService.getHomemadeStores(buyer);
        assertThat(stores).isEmpty();
    }

    @Test
    void nonHomemadeStoreExcluded() {
        Kitchen k = homemadeKitchen();
        Kitchen regular = new Kitchen("aarti-kitchen", "Aarti Kitchen", "Food", "https://example.com/aarti.jpg", seller("9100000001"));
        regular.setId(1L);
        regular.setSellerType(SellerType.KITCHEN);
        regular.setAvailableToday(true);

        when(kitchenRepository.findAll()).thenReturn(List.of(k, regular));
        when(productRepository.findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(any())).thenReturn(List.of());

        User buyer = new User("Buyer", "9876543210", "Lohegaon", UserRole.BUYER);
        buyer.setId(20L);
        buyer.setSociety("Lohegaon");

        var stores = discoveryService.getHomemadeStores(buyer);
        assertThat(stores).hasSize(1);
        assertThat(stores.get(0).getDisplayName()).isEqualTo("Meena's Cakes");
    }

    @Test
    void homemadeStoreExcludedFromFoodAndKitchensDiscovery() {
        Kitchen k = homemadeKitchen();
        Kitchen regular = new Kitchen("aarti-kitchen", "Aarti Kitchen", "Food", "https://example.com/aarti.jpg", seller("9100000001"));
        regular.setId(1L);
        regular.setSellerType(SellerType.KITCHEN);
        regular.setAvailableToday(true);
        regular.setServiceAreas("Lohegaon");

        when(kitchenRepository.findAll()).thenReturn(List.of(k, regular));
        when(productRepository.findAll()).thenReturn(List.of());

        User buyer = new User("Buyer", "9876543210", "Lohegaon", UserRole.BUYER);
        buyer.setId(20L);
        buyer.setSociety("Lohegaon");

        var kitchens = discoveryService.getKitchens("ALL", buyer);
        assertThat(kitchens).hasSize(1);
        assertThat(kitchens.get(0).getDisplayName()).isEqualTo("Aarti Kitchen");
    }

    @Test
    void mixedSellerSeparatesKitchenAndHomemadeProducts() {
        Kitchen bothKitchen = new Kitchen("sunita-store", "Sunita Store", "Food & Cakes", "https://example.com/sunita.jpg", seller("9100000018"));
        bothKitchen.setId(18L);
        bothKitchen.setSellerType(SellerType.BOTH);
        bothKitchen.setAvailableToday(true);
        bothKitchen.setServiceAreas("Lohegaon");

        com.example.my_first_spring_api.model.Product poha = new com.example.my_first_spring_api.model.Product(
                bothKitchen, "Poha", "Breakfast poha", java.math.BigDecimal.valueOf(40), "Per Plate"
        );
        poha.setId(101L);
        poha.setCategory("BREAKFAST");
        poha.setAvailableToday(true);

        com.example.my_first_spring_api.model.Product cake = new com.example.my_first_spring_api.model.Product(
                bothKitchen, "Chocolate Cake", "Homemade cake", java.math.BigDecimal.valueOf(350), "Per Box"
        );
        cake.setId(102L);
        cake.setCategory("CAKES,HOMEMADE");
        cake.setAvailableToday(true);

        when(kitchenRepository.findAll()).thenReturn(List.of(bothKitchen));
        when(productRepository.findByKitchen(bothKitchen)).thenReturn(List.of(poha, cake));

        User buyer = new User("Buyer", "9876543210", "Lohegaon", UserRole.BUYER);
        buyer.setId(20L);
        buyer.setSociety("Lohegaon");

        var homemadeStores = discoveryService.getHomemadeStores(buyer);
        assertThat(homemadeStores).hasSize(1);
        // Only the cake should be counted/named on the Homemade card
        assertThat(homemadeStores.get(0).getItemNames()).containsExactly("Chocolate Cake");
    }
}
