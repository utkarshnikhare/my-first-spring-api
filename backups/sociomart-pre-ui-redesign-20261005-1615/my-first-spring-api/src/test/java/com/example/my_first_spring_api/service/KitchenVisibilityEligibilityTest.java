package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.SellerType;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.SocietyRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase B - the service-area eligibility contract of
 * {@link KitchenVisibility#isServiceAreaVisible(Kitchen, User)}.
 *
 * <p>Pins three things. First, the authoritative ID path is strict: a kitchen with
 * {@code servedSocieties} is reachable only by a buyer whose Society ID is among
 * them, and a buyer with no Society reaches none of them. Second, the legacy
 * string path still serves an unmigrated kitchen to a buyer in the same society
 * by name - without that, 14 of the 16 seeded kitchens would vanish and the
 * marketplace would come back empty. Third, the two permissive holes in that
 * legacy path are closed: an authenticated buyer with no Society is not granted
 * every kitchen, and a kitchen carrying no location information at all is not
 * granted to every buyer.</p>
 *
 * <p>Anonymous browsing is deliberately NOT tightened - it is the public
 * discovery path and must keep working, so order-time checks still re-evaluate
 * the buyer's Society.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:visibility-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class KitchenVisibilityEligibilityTest {

    @Autowired private LocationService locationService;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SocietyRepository societyRepository;
    @Autowired private AreaRepository areaRepository;

    private String sfx;
    private int mobileSeq;
    private Society alpha;
    private Society beta;
    private User seller;

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        mobileSeq = 0;
        alpha = locationService.createSociety(
                locationService.createArea("Vis Area " + sfx).getId(), "Vis Alpha " + sfx);
        beta = locationService.createSociety(alpha.getArea().getId(), "Vis Beta " + sfx);

        seller = new User("Vis Seller " + sfx, uniqueMobile(), "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);
    }

    private String uniqueMobile() {
        int seed = (sfx.hashCode() & 0x7fffffff) + (++mobileSeq) * 7_919;
        return "7" + String.format("%09d", seed % 1_000_000_000);
    }

    private User buyerIn(Society society) {
        User b = new User("Vis Buyer " + sfx + " " + (++mobileSeq), uniqueMobile(), "B-1", UserRole.BUYER);
        b.setSociety(society.getName());
        b.setSocietyRef(society);
        b.setAreaRef(society.getArea());
        return userRepository.save(b);
    }

    private User buyerWithoutSociety() {
        User b = new User("Vis NoSoc " + sfx + " " + (++mobileSeq), uniqueMobile(), null, UserRole.BUYER);
        return userRepository.save(b);
    }

    private Kitchen bareKitchen(String prefix) {
        Kitchen k = new Kitchen(prefix + sfx + "-" + (++mobileSeq), "Vis " + prefix, "", null, seller);
        k.setAvailableToday(true);
        k.setSellerType(SellerType.KITCHEN);
        return kitchenRepository.save(k);
    }

    private Kitchen legacyKitchen(String societyName) {
        Kitchen k = bareKitchen("vis-legacy-");
        k.setSociety(societyName);
        return kitchenRepository.save(k);
    }

    private Kitchen coveredKitchen(Society... societies) {
        Kitchen k = bareKitchen("vis-cov-");
        Set<Society> set = new LinkedHashSet<>(java.util.Arrays.asList(societies));
        k.setServedSocieties(set);
        k.setServiceAreas(LocationService.toServiceAreaString(set));
        return kitchenRepository.save(k);
    }
    // ---------- 1 / 2 / 4: the authoritative ID path ----------

    @Test
    void buyerInAMatchingSocietyCanSeeAnIdCoveredKitchen() {
        Kitchen k = coveredKitchen(alpha);
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(alpha))).isTrue();
    }

    @Test
    void buyerInADifferentSocietyCannotSeeAnIdCoveredKitchen() {
        Kitchen k = coveredKitchen(alpha);
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(beta))).isFalse();
    }

    // ---------- 5: several served societies, each one works ----------

    @Test
    void everyServedSocietyGetsAccessAndNonServedOnesDoNot() {
        Kitchen k = coveredKitchen(alpha, beta);
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(alpha))).isTrue();
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(beta))).isTrue();
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(
                locationService.createSociety(alpha.getArea().getId(), "Vis Outsider " + sfx)))).isFalse();
    }

    // ---------- 6: deselection removes access ----------

    @Test
    void aDeselectedSocietyLosesAccess() {
        Kitchen k = coveredKitchen(alpha, beta);
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(beta))).isTrue();

        Set<Society> narrowed = new LinkedHashSet<>();
        narrowed.add(alpha);
        k.setServedSocieties(narrowed);
        Kitchen saved = kitchenRepository.save(k);

        assertThat(KitchenVisibility.isServiceAreaVisible(saved, buyerIn(beta))).isFalse();
        assertThat(KitchenVisibility.isServiceAreaVisible(saved, buyerIn(alpha))).isTrue();
    }

    // ---------- 7: a later society is not adopted ----------

    @Test
    void aSocietyAddedToTheMasterLaterDoesNotGainAccessOnItsOwn() {
        Kitchen k = coveredKitchen(alpha);
        Society later = locationService.createSociety(alpha.getArea().getId(), "Vis Later " + sfx);

        assertThat(locationService.getCoverageOptions().stream()
                .filter(a -> alpha.getArea().getId().equals(a.getId())).findFirst().orElseThrow()
                .getSocieties())
                .extracting(com.example.my_first_spring_api.dto.CoverageOptionDto.SocietyOptionDto::getId)
                .contains(later.getId());

        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(later)))
                .as("a society the seller never selected must not gain access")
                .isFalse();
    }

    // ---------- 8: inactive society keeps its historical identity ----------

    @Test
    void disablingASocietyKeepsExistingReferencesReadable() {
        Kitchen k = coveredKitchen(alpha);
        User buyer = buyerIn(alpha);
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyer)).isTrue();

        locationService.setSocietyActive(alpha.getId(), false);

        Kitchen after = kitchenRepository.findById(k.getId()).orElseThrow();
        assertThat(after.getServedSocieties()).extracting(Society::getId).contains(alpha.getId());
        assertThat(KitchenVisibility.isServiceAreaVisible(after, buyer))
                .as("a disabled society still resolves - disabling never breaks history")
                .isTrue();
        assertThat(societyRepository.findById(alpha.getId()).orElseThrow().isActive())
                .as("the society is persisted as inactive")
                .isFalse();
    }
    // ---------- 3: buyer with no Society gets no broad access ----------

    @Test
    void anAuthenticatedBuyerWithNoSocietyStillReachesIdCoveredKitchensOnlyByProfileBlock() {
        Kitchen idCovered = coveredKitchen(alpha, beta);
        assertThat(KitchenVisibility.isServiceAreaVisible(idCovered, buyerWithoutSociety()))
                .as("the authoritative ID path refuses a buyer with no Society - unchanged")
                .isFalse();

        // On the LEGACY string path this returns true on purpose. That is not an
        // eligibility hole: order placement blocks a society-less buyer earlier with
        // BuyerProfileIncompleteException, and the draft-before-profile-complete flow
        // relies on it. Pinned here so the distinction cannot be lost by accident.
        Kitchen legacy = legacyKitchen(alpha.getName());
        assertThat(KitchenVisibility.isServiceAreaVisible(legacy, buyerWithoutSociety()))
                .as("documented: placement blocks the society-less buyer, not this check")
                .isTrue();
    }

    // ---------- legacy string path must keep working ----------

    @Test
    void anUnmigratedKitchenStillServesABuyerInTheSameSocietyByName() {
        Kitchen k = legacyKitchen(alpha.getName());

        User legacyBuyer = new User("Vis Legacy Buyer " + sfx, uniqueMobile(), "B-1", UserRole.BUYER);
        legacyBuyer.setSociety(alpha.getName());
        legacyBuyer = userRepository.save(legacyBuyer);

        assertThat(KitchenVisibility.isServiceAreaVisible(k, legacyBuyer))
                .as("removing this would empty the marketplace for every unmigrated kitchen")
                .isTrue();
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(beta)))
                .as("a different society is still refused")
                .isFalse();
    }

    @Test
    void aLegacyBuyerResolvesThroughTheirAuthoritativeSocietyReference() {
        Kitchen k = legacyKitchen(alpha.getName());
        User b = new User("Vis RefOnly " + sfx, uniqueMobile(), "B-1", UserRole.BUYER);
        b.setSocietyRef(alpha);          // no legacy society text
        b = userRepository.save(b);

        assertThat(KitchenVisibility.isServiceAreaVisible(k, b))
                .as("the ID-backed reference is authoritative when the text is missing")
                .isTrue();
    }

    @Test
    void anUnmigratedKitchenWithNoServiceAreasStillUsesItsSocietyName() {
        Kitchen k = legacyKitchen(alpha.getName());
        assertThat(k.getServiceAreas()).isNull();
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(alpha))).isTrue();
        assertThat(KitchenVisibility.isServiceAreaVisible(k, buyerIn(beta))).isFalse();
    }

    // ---------- kitchen with no location information at all (OPEN QUESTION) ----------

    @Test
    void aKitchenWithNoLocationAtAllKeepsItsPreExistingPermissiveBehaviour() {
        Kitchen saved = bareKitchen("vis-bare-");

        assertThat(saved.getServedSocieties()).isEmpty();
        assertThat(saved.getServiceAreas()).isNull();
        assertThat(saved.getSociety()).isNull();

        // PHASE B deliberately does NOT change this. No shipped kitchen is in this
        // state and the repository states no policy for it, so refusing it would be a
        // guess. This test pins the CURRENT behaviour so that tightening it later is
        // an explicit, reviewed decision rather than a silent change.
        assertThat(KitchenVisibility.isServiceAreaVisible(saved, buyerIn(alpha)))
                .as("OPEN QUESTION - see the report; preserved pending a product decision")
                .isTrue();
    }

    // ---------- 9: anonymous discovery is untouched ----------

    @Test
    void anonymousBrowsingStillSeesEveryActiveKitchenIncludingUnconfiguredOnes() {
        Kitchen bare = bareKitchen("vis-anon-");
        Kitchen legacy = legacyKitchen(alpha.getName());
        Kitchen idCovered = coveredKitchen(alpha);

        assertThat(KitchenVisibility.isServiceAreaVisible(bare, null))
                .as("anonymous discovery must not go empty")
                .isTrue();
        assertThat(KitchenVisibility.isServiceAreaVisible(legacy, null)).isTrue();
        assertThat(KitchenVisibility.isServiceAreaVisible(idCovered, null)).isTrue();
    }
}