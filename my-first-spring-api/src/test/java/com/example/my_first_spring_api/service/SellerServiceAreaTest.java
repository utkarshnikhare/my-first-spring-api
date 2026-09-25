package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderItemRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Requirement 20 — Seller → Manage Kitchen "Who can order from me?".
 * The seller can only update their own kitchen's service area, only with
 * existing societies, and duplicates are handled safely.
 */
class SellerServiceAreaTest {

    @Mock private KitchenRepository kitchenRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderService orderService;
    @Mock private FeatureService featureService;
    @Mock private UserRepository userRepository;

    private SellerService sellerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sellerService = new SellerService(kitchenRepository, productRepository, orderItemRepository,
                orderService, featureService, new SocietyDirectory(userRepository, kitchenRepository));
        when(userRepository.findAll()).thenReturn(List.of(
                user("Buyer A", "9876500001", "Alpha Society"),
                user("Buyer B", "9876500002", "Beta Society")));
    }

    private User user(String name, String mobile, String society) {
        User u = new User(name, mobile, "A-1", UserRole.BUYER);
        u.setSociety(society);
        return u;
    }

    private User seller(long id) {
        User s = new User("Seller" + id, "91000000" + id, "S-1", UserRole.SELLER);
        s.setId(id);
        return s;
    }

    private Kitchen ownedKitchen(User owner) {
        Kitchen k = new Kitchen("k" + owner.getId(), "Kitchen " + owner.getId(), "d", null, owner);
        k.setId(1L);
        k.setSociety("Home Society");
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(k));
        when(kitchenRepository.save(any(Kitchen.class))).thenAnswer(inv -> inv.getArgument(0));
        return k;
    }

    private KitchenUpdateDto dtoWithServiceAreas(String serviceAreas) {
        KitchenUpdateDto dto = new KitchenUpdateDto();
        dto.setServiceAreas(serviceAreas);
        return dto;
    }

    @Test
    void sellerCanSaveSingleSociety() {
        User s = seller(10L);
        Kitchen kitchen = ownedKitchen(s);

        var result = sellerService.updateKitchen(1L, dtoWithServiceAreas("Alpha Society"), s);

        assertThat(result.getServiceAreas()).isEqualTo("Alpha Society");
        assertThat(kitchen.getServiceAreas()).isEqualTo("Alpha Society");
        verify(kitchenRepository).save(kitchen);
    }

    @Test
    void sellerCanSaveMultipleSocieties() {
        User s = seller(10L);
        Kitchen kitchen = ownedKitchen(s);

        var result = sellerService.updateKitchen(1L, dtoWithServiceAreas("Alpha Society,Beta Society"), s);

        assertThat(result.getServiceAreas()).isEqualTo("Alpha Society,Beta Society");
        assertThat(kitchen.getServiceAreas()).isEqualTo("Alpha Society,Beta Society");
    }

    @Test
    void savedServiceAreasPersistOnReload() {
        User s = seller(10L);
        ownedKitchen(s);

        sellerService.updateKitchen(1L, dtoWithServiceAreas("Beta Society"), s);

        Kitchen reloaded = kitchenRepository.findById(1L).orElseThrow();
        assertThat(reloaded.getServiceAreas()).isEqualTo("Beta Society");
    }

    @Test
    void sellerCanRemoveAllSocieties() {
        User s = seller(10L);
        Kitchen kitchen = ownedKitchen(s);
        kitchen.setServiceAreas("Alpha Society");

        var result = sellerService.updateKitchen(1L, dtoWithServiceAreas("  "), s);

        // Empty selection clears service areas; the existing empty-service-area
        // semantics (fall back to the kitchen's primary society) are preserved.
        assertThat(result.getServiceAreas()).isEmpty();
        assertThat(kitchen.getServiceAreas()).isEmpty();
    }

    @Test
    void invalidSocietyIsRejectedAndNothingChanges() {
        User s = seller(10L);
        Kitchen kitchen = ownedKitchen(s);
        kitchen.setServiceAreas("Alpha Society");

        assertThatThrownBy(() -> sellerService.updateKitchen(1L, dtoWithServiceAreas("Not A Society"), s))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not A Society");

        assertThat(kitchen.getServiceAreas()).isEqualTo("Alpha Society");
        verify(kitchenRepository, never()).save(any(Kitchen.class));
    }

    @Test
    void duplicateSelectionsAreHandledSafely() {
        User s = seller(10L);
        Kitchen kitchen = ownedKitchen(s);

        var result = sellerService.updateKitchen(1L,
                dtoWithServiceAreas("Alpha Society,alpha society,BETA SOCIETY,beta society"), s);

        assertThat(result.getServiceAreas()).isEqualTo("Alpha Society,Beta Society");
        assertThat(kitchen.getServiceAreas()).isEqualTo("Alpha Society,Beta Society");
    }

    @Test
    void sellerCannotModifyAnotherSellersServiceArea() {
        User owner = seller(10L);
        ownedKitchen(owner);
        User intruder = seller(20L);

        assertThatThrownBy(() -> sellerService.updateKitchen(1L, dtoWithServiceAreas("Beta Society"), intruder))
                .isInstanceOf(SellerNotAuthorizedException.class);

        assertThat(kitchenRepository.findById(1L).orElseThrow().getServiceAreas()).isNull();
    }

    @Test
    void sellerReceivesExistingSocietiesForSelection() {
        Kitchen existing = new Kitchen("kh", "KH", "d", null, seller(10L));
        existing.setSociety("Home Society");
        when(kitchenRepository.findAll()).thenReturn(List.of(existing));

        List<String> societies = sellerService.getKnownSocieties();

        assertThat(societies).containsExactlyInAnyOrder("Alpha Society", "Beta Society", "Home Society");
    }
}