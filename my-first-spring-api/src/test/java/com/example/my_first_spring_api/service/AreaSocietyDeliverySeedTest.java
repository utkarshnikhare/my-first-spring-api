package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.DemoDataSeeder;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.repository.EnquiryRepository;
import com.example.my_first_spring_api.repository.FavouriteRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.PlatformSettingRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.SellerTemplateRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression cover for the approved Area -&gt; Society mapping being orderable.
 *
 * <p>Root cause: "Pride World City" was seeded as the society inside the
 * "Charholi / Lohegaon" Area, so it appeared in the Buyer society dropdown and in
 * the Seller service-area dropdown, but no kitchen actually served it.
 * {@code KitchenVisibility.isServiceAreaVisible} therefore hid every kitchen from
 * a buyer in that society, so order placement failed with "This kitchen is not
 * currently accepting orders in your area."
 *
 * <p>These tests pin the fix: the approved society gains genuine serving kitchens,
 * the selection is additive and idempotent, and no other seller is affected.
 */
class AreaSocietyDeliverySeedTest {

    private static final String SOCIETY = "Pride World City";

    @Mock private UserRepository userRepository;
    @Mock private KitchenRepository kitchenRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PlatformSettingRepository platformSettingRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private EnquiryRepository enquiryRepository;
    @Mock private FavouriteRepository favouriteRepository;
    @Mock private SellerTemplateRepository sellerTemplateRepository;
    @Mock private AreaRepository areaRepository;
    @Mock private com.example.my_first_spring_api.repository.SocietyRepository societyRepository;

    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        seeder = new DemoDataSeeder(userRepository, kitchenRepository, productRepository,
                platformSettingRepository, orderRepository, enquiryRepository,
                favouriteRepository, sellerTemplateRepository, areaRepository, societyRepository);
    }

    private User seller(String mobile) {
        User s = new User("Seller", mobile, "S-1", UserRole.SELLER);
        s.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return s;
    }

    /** Wires the designated seller with one kitchen carrying the given selection. */
    private Kitchen givenSellerWithKitchen(String serviceAreas) {
        User s = seller("9100000016");
        Kitchen k = new Kitchen();
        k.setSociety("Lohegaon");
        k.setServiceAreas(serviceAreas);
        when(userRepository.findByMobileNumber("9100000016")).thenReturn(Optional.of(s));
        when(kitchenRepository.findBySeller(s)).thenReturn(List.of(k));
        when(userRepository.findAll()).thenReturn(List.of(s));
        when(userRepository.findByMobileNumber("9876500016")).thenReturn(Optional.empty());
        return k;
    }

    @Test
    void addsTheApprovedSocietyToTheDesignatedSellerKitchen() {
        Kitchen k = givenSellerWithKitchen(null);

        seeder.seedAreaSocietyDeliveryIfNeeded();

        assertThat(k.getServiceAreas()).as("the kitchen now serves the approved society").isEqualTo(SOCIETY);
        assertThat(k.getSociety()).as("the kitchen's own society is untouched").isEqualTo("Lohegaon");
        verify(kitchenRepository).save(any(Kitchen.class));
    }

    @Test
    void isIdempotentAndDoesNotDuplicateTheSociety() {
        // Already serves the society in a different casing - the same society.
        Kitchen k = givenSellerWithKitchen("pride world city");

        seeder.seedAreaSocietyDeliveryIfNeeded();

        assertThat(k.getServiceAreas())
                .as("a case variant is the same society, not a new one")
                .isEqualTo("pride world city");
        verify(kitchenRepository, never()).save(any(Kitchen.class));
    }

    @Test
    void preservesExistingServiceAreasAndOnlyAppends() {
        Kitchen k = givenSellerWithKitchen("Green Valley, Lake View");

        seeder.seedAreaSocietyDeliveryIfNeeded();

        assertThat(k.getServiceAreas())
                .as("existing selections are kept and the society is appended")
                .isEqualTo("Green Valley, Lake View," + SOCIETY);
    }

    @Test
    void neverInventsASellerAccount() {
        when(userRepository.findByMobileNumber("9100000016")).thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber("9100000001")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.findByMobileNumber("9876500016")).thenReturn(Optional.empty());

        seeder.seedAreaSocietyDeliveryIfNeeded();

        verify(kitchenRepository, never()).save(any(Kitchen.class));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        // Only the demo buyer may be created, and only as a BUYER.
        verify(userRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.BUYER);
        assertThat(saved.getValue().getSociety()).isEqualTo(SOCIETY);
    }

    @Test
    void doesNotCreateASecondBuyerWhenOneAlreadyLivesInTheSociety() {
        User existingBuyer = new User("Someone", "9000009999", "X-1", UserRole.BUYER);
        existingBuyer.setSociety(SOCIETY);
        when(userRepository.findByMobileNumber("9100000016")).thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber("9100000001")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of(existingBuyer));
        when(userRepository.findByMobileNumber("9876500016")).thenReturn(Optional.empty());

        seeder.seedAreaSocietyDeliveryIfNeeded();

        verify(userRepository, never()).save(any(User.class));
    }

}
