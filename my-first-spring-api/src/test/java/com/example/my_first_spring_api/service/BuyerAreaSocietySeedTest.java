package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.DemoDataSeeder;
import com.example.my_first_spring_api.dto.AreaDto;
import com.example.my_first_spring_api.dto.BuyerProfileDto;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FIX ONE - approved demo Area -&gt; Society mapping.
 *
 * <p>Covers the seed being idempotent and additive, the dependent dropdown data,
 * persistence of both selections, the backend rejections for unknown area,
 * unknown society and a society from a different area, and compatibility of
 * profiles that predate areas.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:area-society-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("demo")
class BuyerAreaSocietySeedTest {

    private static final String AREA = "Charholi / Lohegaon";
    private static final String SOCIETY = "Pride World City";

    @Autowired DemoDataSeeder seeder;
    @Autowired BuyerService buyerService;
    @Autowired AreaRepository areas;
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;

    @BeforeEach
    void seed() {
        seeder.seedAll();
    }

    private MockHttpSession buyerSession() {
        User buyer = users.findByRole(UserRole.BUYER).get(0);
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        return s;
    }

    // ---------- seed ----------

    @Test
    void seedsTheApprovedAreaWithItsApprovedSociety() {
        Area area = areas.findByNameIgnoreCase(AREA).orElseThrow();
        assertThat(area.getName()).isEqualTo(AREA);
        assertThat(area.getSocieties()).contains(SOCIETY);
    }

    @Test
    void repeatedSeedingDoesNotDuplicateTheAreaOrTheSociety() {
        long before = areas.count();
        seeder.seedAreasIfEmpty();
        seeder.seedAll();
        seeder.seedAreasIfEmpty();

        assertThat(areas.count()).as("no duplicate area rows").isEqualTo(before);
        assertThat(areas.findByNameIgnoreCase(AREA).orElseThrow().getSocieties())
                .as("society listed once, however often we seed")
                .containsOnlyOnce(SOCIETY);
    }

    @Test
    void reseedingPreservesExistingAreaSocietiesAndDemoData() {
        Area area = areas.findByNameIgnoreCase(AREA).orElseThrow();
        area.getSocieties().add("Some Existing Society");
        areas.save(area);
        long kitchensBefore = kitchens.count();
        long productsBefore = products.count();

        seeder.seedAreasIfEmpty();

        assertThat(areas.findByNameIgnoreCase(AREA).orElseThrow().getSocieties())
                .as("seeding is additive - it does not wipe existing relationships")
                .contains("Some Existing Society", SOCIETY);
        assertThat(kitchens.count()).isEqualTo(kitchensBefore);
        assertThat(products.count()).isEqualTo(productsBefore);
    }

    @Test
    void aCaseVariantOfTheSocietyIsNotAddedAsADuplicate() {
        Area area = areas.findByNameIgnoreCase(AREA).orElseThrow();
        area.getSocieties().clear();
        area.getSocieties().add("pride world city"); // same society, different case
        areas.save(area);

        seeder.seedAreasIfEmpty();

        assertThat(areas.findByNameIgnoreCase(AREA).orElseThrow().getSocieties())
                .as("a formatting variant is the same society, not a new one")
                .containsExactly("pride world city");
    }

    // ---------- dropdown data ----------

    @Test
    void areasExposeOnlyTheirOwnSocieties() {
        List<AreaDto> list = buyerService.getAreas(buyerSession());
        assertThat(list).isNotEmpty();
        AreaDto area = list.stream().filter(a -> AREA.equals(a.getName())).findFirst().orElseThrow();
        assertThat(area.getSocieties()).contains(SOCIETY);
    }

    @Test
    void theApprovedSocietyIsAlsoAKnownSocietyForTheExistingDirectory() {
        // Area societies join the ONE existing directory rather than a second one.
        assertThat(buyerService.getSelectableSocieties(buyerSession())).contains(SOCIETY);
    }

    // ---------- persistence ----------

    @Test
    void savesBothAreaAndSocietyAndRestoresThem() {
        MockHttpSession s = buyerSession();
        BuyerProfileDto dto = new BuyerProfileDto();
        dto.setArea(AREA);
        dto.setSociety(SOCIETY);

        assertThat(buyerService.updateProfile(dto, s).getArea()).isEqualTo(AREA);
        assertThat(buyerService.getProfile(s).getArea()).isEqualTo(AREA);
        assertThat(buyerService.getProfile(s).getSociety()).isEqualTo(SOCIETY);
    }

    @Test
    void areaNameIsCanonicalisedNotDuplicatedByCase() {
        MockHttpSession s = buyerSession();
        BuyerProfileDto dto = new BuyerProfileDto();
        dto.setArea("  charholi / LOHEGAON  ");
        assertThat(buyerService.updateProfile(dto, s).getArea()).isEqualTo(AREA);
        assertThat(areas.findByNameIgnoreCase(AREA)).isPresent();
    }

    // ---------- backend rejections ----------

    @Test
    void rejectsAnUnknownArea() {
        MockHttpSession s = buyerSession();
        BuyerProfileDto dto = new BuyerProfileDto();
        dto.setArea("Some Area That Does Not Exist");
        assertThatThrownBy(() -> buyerService.updateProfile(dto, s))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownSociety() {
        MockHttpSession s = buyerSession();
        BuyerProfileDto dto = new BuyerProfileDto();
        dto.setArea(AREA);
        dto.setSociety("A Society Nobody Registered");
        assertThatThrownBy(() -> buyerService.updateProfile(dto, s))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsASocietyThatBelongsToADifferentArea() {
        // "Lohegaon" is a real society in the directory but is NOT inside the
        // approved area, so the pair must be refused on the server.
        MockHttpSession s = buyerSession();
        assertThat(buyerService.getSelectableSocieties(s)).contains("Lohegaon");

        BuyerProfileDto dto = new BuyerProfileDto();
        dto.setArea(AREA);
        dto.setSociety("Lohegaon");
        assertThatThrownBy(() -> buyerService.updateProfile(dto, s))
                .as("a real society from another area is still an invalid combination")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("area");
    }

    @Test
    void aRejectedCombinationIsNotPersisted() {
        MockHttpSession s = buyerSession();
        BuyerProfileDto good = new BuyerProfileDto();
        good.setArea(AREA);
        good.setSociety(SOCIETY);
        buyerService.updateProfile(good, s);

        BuyerProfileDto bad = new BuyerProfileDto();
        bad.setArea(AREA);
        bad.setSociety("Lohegaon");
        assertThatThrownBy(() -> buyerService.updateProfile(bad, s)).isInstanceOf(IllegalArgumentException.class);

        assertThat(buyerService.getProfile(s).getSociety())
                .as("the failed save must not have changed the stored society")
                .isEqualTo(SOCIETY);
    }

    // ---------- compatibility ----------

    @Test
    void aProfileWithASocietyButNoAreaStaysValidAndEditable() {
        MockHttpSession s = buyerSession();
        // Simulate a pre-Area profile: a society, no area.
        User buyer = users.findByRole(UserRole.BUYER).get(0);
        String original = buyer.getSociety();
        buyer.setArea(null);
        buyer.setSociety(original);
        users.save(buyer);

        BuyerProfileDto save = new BuyerProfileDto();
        save.setName("Renamed Buyer");
        save.setSociety(original);   // save without touching the community
        BuyerProfileDto after = buyerService.updateProfile(save, s);

        assertThat(after.getSociety()).isEqualTo(original);
        assertThat(after.getArea()).as("no area was chosen, so none is invented").isNull();
        assertThat(after.getName()).isEqualTo("Renamed Buyer");
    }

    @Test
    void existingProfileKeepsWorkingWhenTheAreaIsCleared() {
        MockHttpSession s = buyerSession();
        BuyerProfileDto dto = new BuyerProfileDto();
        dto.setArea(AREA);
        dto.setSociety(SOCIETY);
        buyerService.updateProfile(dto, s);

        BuyerProfileDto clear = new BuyerProfileDto();
        clear.setArea("");
        clear.setSociety("");
        assertThat(buyerService.updateProfile(clear, s).getArea()).isEmpty();
    }
}
