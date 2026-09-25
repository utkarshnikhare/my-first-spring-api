package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Requirement 20 — service areas reference existing societies only.
 * The directory is derived from existing platform data (no duplicate society records).
 */
class SocietyDirectoryTest {

    @Mock private UserRepository userRepository;
    @Mock private KitchenRepository kitchenRepository;

    @InjectMocks private SocietyDirectory societyDirectory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(userRepository.findAll()).thenReturn(List.of(
                user("Buyer", "9876500001", "Sunshine Society"),
                user("Seller", "9100000001", "Green Valley")));
        Kitchen kitchen = new Kitchen("k1", "Kitchen", "d", null, user("Owner", "9100000002", "Lake View"));
        kitchen.setSociety("Lake View");
        kitchen.setServiceAreas("Hill Side, Riverside");
        when(kitchenRepository.findAll()).thenReturn(List.of(kitchen));
    }

    private User user(String name, String mobile, String society) {
        User u = new User(name, mobile, "A-1", UserRole.BUYER);
        u.setSociety(society);
        return u;
    }

    @Test
    void existingSocietiesAreDerivedFromUsersAndKitchens() {
        List<String> societies = societyDirectory.findAllSocieties();

        assertThat(societies).containsExactlyInAnyOrder(
                "Sunshine Society", "Green Valley", "Lake View", "Hill Side", "Riverside");
    }

    @Test
    void existingSocietiesAreDeduplicatedCaseInsensitively() {
        when(userRepository.findAll()).thenReturn(List.of(
                user("B", "9876500001", "Sunshine Society"),
                user("C", "9876500002", "sunshine society")));
        when(kitchenRepository.findAll()).thenReturn(List.of());

        assertThat(societyDirectory.findAllSocieties()).containsExactly("Sunshine Society");
    }

    @Test
    void acceptsKnownSocietyAndNormalizesToStoredSpelling() {
        String normalized = societyDirectory.validateAndNormalize("  sunshine society ,lake view ");

        // Canonicalized to the stored spelling and sorted deterministically.
        assertThat(normalized).isEqualTo("Lake View,Sunshine Society");
    }

    @Test
    void rejectsUnknownSociety() {
        assertThatThrownBy(() -> societyDirectory.validateAndNormalize("Nowhere Society"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nowhere Society");
    }

    @Test
    void duplicateSelectionsAreCollapsedSafely() {
        String normalized = societyDirectory.validateAndNormalize(
                "Lake View,lake view,LAKE VIEW,Hill Side");

        // Case variants collapse into one entry; result order is deterministic.
        assertThat(normalized).isEqualTo("Hill Side,Lake View");
    }

    @Test
    void blankSelectionClearsServiceAreas() {
        assertThat(societyDirectory.validateAndNormalize(null)).isEmpty();
        assertThat(societyDirectory.validateAndNormalize("   ")).isEmpty();
        assertThat(societyDirectory.validateAndNormalize("")).isEmpty();
    }

    @Test
    void alreadyStoredServiceAreasRemainSelectable() {
        // "Hill Side" exists only because a kitchen already stores it — it must stay editable.
        assertThat(societyDirectory.validateAndNormalize("Hill Side")).isEqualTo("Hill Side");
    }

    @Test
    void findAllSocietiesIsSortedCaseInsensitively() {
        List<String> societies = societyDirectory.findAllSocieties();

        assertThat(societies).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }
}