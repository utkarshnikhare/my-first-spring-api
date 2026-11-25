package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.SellerRegistrationRequestDto;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seller-multiarea-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.auth.direct-auth-enabled=true"
})
@ActiveProfiles("test")
@Transactional
class SellerMultiAreaCoverageRegistrationTest {

    @Autowired private AuthService authService;
    @Autowired private LocationService locationService;
    @Autowired private KitchenRepository kitchenRepository;

    private String sfx;
    private Area charholi;
    private Area lohegaon;
    private Area dhanori;
    private Society kingsbury;
    private Society manhattan;
    private Society prideWorldCity;
    private Society nivasaUdan;
    private Society splendidCounty;
    private Society revelOrchid;

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        charholi = locationService.createArea("Charholi " + sfx);
        kingsbury = locationService.createSociety(charholi.getId(), "Kingsbury " + sfx);
        manhattan = locationService.createSociety(charholi.getId(), "Manhattan " + sfx);
        prideWorldCity = locationService.createSociety(charholi.getId(), "Pride World City " + sfx);

        lohegaon = locationService.createArea("Lohegaon " + sfx);
        nivasaUdan = locationService.createSociety(lohegaon.getId(), "Nivasa Udan " + sfx);
        splendidCounty = locationService.createSociety(lohegaon.getId(), "Splendid County " + sfx);

        dhanori = locationService.createArea("Dhanori " + sfx);
        revelOrchid = locationService.createSociety(dhanori.getId(), "Revel Orchid " + sfx);
    }

    private SellerRegistrationRequestDto validRequest(String mobile, List<Long> coverageSocietyIds) {
        SellerRegistrationRequestDto req = new SellerRegistrationRequestDto();
        req.setSellerName("Chef " + sfx);
        req.setMobileNumber(mobile);
        req.setPassword("Valid-Password-12345");
        req.setWhatsappNumber(mobile);
        req.setAlternateContact("");
        req.setKitchenName("Kitchen " + sfx);
        req.setKitchenSlug("kitchen-" + sfx);
        req.setSpeciality("Multi-Area Catering");
        req.setSellerCategory("BOTH");
        req.setAcceptedPaymentMode("CASH_ONLY");
        req.setShortDescription("Fresh homemade food delivered across communities.");
        req.setInstagramLink("");
        req.setPrimaryAreaId(charholi.getId());
        req.setPrimarySocietyId(kingsbury.getId());
        req.setBuilding("Tower A");
        req.setServiceSocietyIds(coverageSocietyIds);
        return req;
    }

    @Test
    void sellerCanRegisterWithSocietiesAcrossMultipleAreas() {
        // Spec scenario: Select all 3 Charholi societies + Nivasa Udan in Lohegaon (zero in Dhanori)
        List<Long> multiAreaCoverage = List.of(
                kingsbury.getId(),
                manhattan.getId(),
                prideWorldCity.getId(),
                nivasaUdan.getId()
        );

        String mobile = "98" + String.format("%08d", Math.abs((sfx + "1").hashCode()) % 100_000_000);
        SellerRegistrationRequestDto request = validRequest(mobile, multiAreaCoverage);

        AuthService.SellerRegistration registration = authService.registerSeller(request);

        User seller = registration.user();
        assertThat(seller).isNotNull();
        assertThat(seller.getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.PENDING);
        // Primary location represents pickup address
        assertThat(seller.getArea()).isEqualTo(charholi.getName());
        assertThat(seller.getSociety()).isEqualTo(kingsbury.getName());

        Kitchen kitchen = kitchenRepository.findByName(registration.kitchenSlug()).orElseThrow();
        Set<Long> savedCoverageIds = kitchen.getServedSocieties().stream()
                .map(Society::getId)
                .collect(Collectors.toSet());

        // All 4 chosen societies across both areas must be saved in kitchen_served_societies
        assertThat(savedCoverageIds).containsExactlyInAnyOrder(
                kingsbury.getId(),
                manhattan.getId(),
                prideWorldCity.getId(),
                nivasaUdan.getId()
        );
        assertThat(savedCoverageIds).doesNotContain(splendidCounty.getId(), revelOrchid.getId());

        // Test buyer service eligibility across areas
        assertThat(locationService.serves(kitchen, kingsbury)).isTrue();
        assertThat(locationService.serves(kitchen, manhattan)).isTrue();
        assertThat(locationService.serves(kitchen, prideWorldCity)).isTrue();
        assertThat(locationService.serves(kitchen, nivasaUdan)).isTrue();

        // Not served:
        assertThat(locationService.serves(kitchen, splendidCounty)).isFalse();
        assertThat(locationService.serves(kitchen, revelOrchid)).isFalse();
    }

    @Test
    void singleAreaRegistrationWithExplicitServiceAreaIdPreservesBackwardCompatibility() {
        List<Long> singleAreaCoverage = List.of(kingsbury.getId(), manhattan.getId());
        String mobile = "98" + String.format("%08d", Math.abs((sfx + "2").hashCode()) % 100_000_000);
        SellerRegistrationRequestDto request = validRequest(mobile, singleAreaCoverage);
        request.setServiceAreaId(charholi.getId());

        AuthService.SellerRegistration registration = authService.registerSeller(request);
        Kitchen kitchen = kitchenRepository.findByName(registration.kitchenSlug()).orElseThrow();

        Set<Long> savedCoverageIds = kitchen.getServedSocieties().stream()
                .map(Society::getId)
                .collect(Collectors.toSet());

        assertThat(savedCoverageIds).containsExactlyInAnyOrder(kingsbury.getId(), manhattan.getId());
    }

    @Test
    void sellerRegistrationWithInactiveSocietyIsRejected() {
        locationService.setSocietyActive(manhattan.getId(), false);

        List<Long> coverage = List.of(kingsbury.getId(), manhattan.getId());
        String mobile = "98" + String.format("%08d", Math.abs((sfx + "3").hashCode()) % 100_000_000);
        SellerRegistrationRequestDto request = validRequest(mobile, coverage);

        assertThatThrownBy(() -> authService.registerSeller(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void sellerRegistrationWithUnknownSocietyIsRejected() {
        List<Long> coverage = List.of(kingsbury.getId(), 999_999_999L);
        String mobile = "98" + String.format("%08d", Math.abs((sfx + "4").hashCode()) % 100_000_000);
        SellerRegistrationRequestDto request = validRequest(mobile, coverage);

        assertThatThrownBy(() -> authService.registerSeller(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown community");
    }

    @Test
    void sellerRegistrationWithEmptyCoverageIsRejected() {
        String mobile = "98" + String.format("%08d", Math.abs((sfx + "5").hashCode()) % 100_000_000);
        SellerRegistrationRequestDto request = validRequest(mobile, List.of());

        assertThatThrownBy(() -> authService.registerSeller(request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
