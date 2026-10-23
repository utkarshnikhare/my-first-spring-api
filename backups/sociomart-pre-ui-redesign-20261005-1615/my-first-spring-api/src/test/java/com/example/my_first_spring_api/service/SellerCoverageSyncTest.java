package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
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
 * Coverage write-path synchronisation: when a seller (Manage Kitchen) or an
 * Admin (kitchen service-areas) saves a service-area selection expressed as
 * society NAMES, the ID-backed coverage that {@link KitchenVisibility} treats
 * as authoritative must follow that selection.
 *
 * <p>The defect this pins: the boot-time migration fills
 * {@code Kitchen.servedSocieties} from the legacy display string, after which
 * the ID path wins in {@code isServiceAreaVisible}. The seller/admin save paths
 * only rewrote the display string, so once a kitchen had been migrated a later
 * coverage change was silently ignored - buyers in the newly selected societies
 * stayed blocked and buyers in the removed ones stayed eligible (regression
 * scenario 6: "seller coverage changing before checkout").</p>
 *
 * <p>The "never guess" rule of the migration carries over: a name that is
 * unknown or ambiguous under the master drops the kitchen back onto the
 * original string comparison instead of being partially mapped.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:coverage-sync-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SellerCoverageSyncTest {

    @Autowired private LocationService locationService;
    @Autowired private SellerService sellerService;
    @Autowired private AdminService adminService;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SocietyRepository societyRepository;

    private String sfx;
    private int mobileSeq;
    private Society alpha;
    private Society beta;
    private User seller;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        mobileSeq = 0;
        Area area = locationService.createArea("Sync Area " + sfx);
        alpha = locationService.createSociety(area.getId(), "Sync Alpha " + sfx);
        beta = locationService.createSociety(area.getId(), "Sync Beta " + sfx);

        seller = new User("Sync Seller " + sfx, uniqueMobile(), "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);

        kitchen = kitchenRepository.save(new Kitchen("sync-k-" + sfx, "Sync Kitchen", "", null, seller));
        // A migrated record: explicit Society IDs in place, display string in step.
        kitchen.setServiceAreas(alpha.getName());
        kitchen.setServedSocieties(new LinkedHashSet<>(Set.of(alpha)));
        kitchen = kitchenRepository.save(kitchen);
    }

    private KitchenUpdateDto dto(String serviceAreas) {
        KitchenUpdateDto d = new KitchenUpdateDto();
        d.setServiceAreas(serviceAreas);
        return d;
    }

    private Kitchen reload() {
        return kitchenRepository.findById(kitchen.getId()).orElseThrow();
    }

    private User buyerIn(Society society) {
        User b = new User("Sync Buyer " + sfx + " " + (++mobileSeq), uniqueMobile(), "B-1", UserRole.BUYER);
        b.setSociety(society.getName());
        b.setSocietyRef(society);
        b.setAreaRef(society.getArea());
        return b;
    }

    private String uniqueMobile() {
        int seed = (sfx.hashCode() & 0x7fffffff) + (++mobileSeq) * 7_919;
        return "9" + String.format("%09d", seed % 1_000_000_000);
    }
    // ---------------- regression 6: seller coverage change before checkout ----------------

    @Test
    void sellerCoverageChangeAppliesImmediately() {
        sellerService.updateKitchen(kitchen.getId(), dto(beta.getName()), seller);

        Kitchen reloaded = reload();
        assertThat(reloaded.getServiceAreas()).isEqualTo(beta.getName());
        assertThat(reloaded.getServedSocieties())
                .as("ID coverage follows the seller's new selection")
                .extracting(Society::getId).containsExactly(beta.getId());

        assertThat(KitchenVisibility.isServiceAreaVisible(reloaded, buyerIn(beta)))
                .as("buyer in the newly selected Society can order")
                .isTrue();
        assertThat(KitchenVisibility.isServiceAreaVisible(reloaded, buyerIn(alpha)))
                .as("buyer in the removed Society can no longer order")
                .isFalse();
    }

    @Test
    void adminCoverageChangeAppliesImmediately() {
        adminService.updateKitchenServiceAreas(kitchen.getId(), beta.getName());

        Kitchen reloaded = reload();
        assertThat(reloaded.getServiceAreas()).isEqualTo(beta.getName());
        assertThat(reloaded.getServedSocieties())
                .as("ID coverage follows the Admin's edit too")
                .extracting(Society::getId).containsExactly(beta.getId());
    }

    // ---------------- the "never guess" rule carries over ----------------

    @Test
    void unresolvableNameFallsBackToTheStringPath() {
        // A legacy society the directory knows (a buyer profile references it)
        // but that has NO master record: resolving it to an ID would be a guess.
        String legacy = "Legacy Sync Soc " + sfx;
        User legacyHolder = new User("Legacy Holder " + sfx, uniqueMobile(), "L-1", UserRole.BUYER);
        legacyHolder.setSociety(legacy);
        userRepository.save(legacyHolder);

        sellerService.updateKitchen(kitchen.getId(), dto(legacy), seller);

        Kitchen reloaded = reload();
        assertThat(reloaded.getServiceAreas()).isEqualTo(legacy);
        assertThat(reloaded.getServedSocieties())
                .as("unresolvable names drop the kitchen onto the original string path")
                .isEmpty();

        User legacyBuyer = new User("Legacy Buyer " + sfx, uniqueMobile(), "L-2", UserRole.BUYER);
        legacyBuyer.setSociety(legacy); // no ID reference - pure legacy profile
        assertThat(KitchenVisibility.isServiceAreaVisible(reloaded, legacyBuyer))
                .as("string comparison still serves the legacy selection")
                .isTrue();
        assertThat(KitchenVisibility.isServiceAreaVisible(reloaded, buyerIn(alpha)))
                .as("the stale ID coverage does not leak through")
                .isFalse();
    }

    @Test
    void blankSelectionClearsExplicitCoverage() {
        sellerService.updateKitchen(kitchen.getId(), dto(""), seller);

        Kitchen reloaded = reload();
        assertThat(reloaded.getServiceAreas()).isEmpty();
        assertThat(reloaded.getServedSocieties())
                .as("blank selection removes explicit coverage; the existing blank "
                        + "semantics (fall back to the kitchen's primary society) apply")
                .isEmpty();
    }

    @Test
    void resolvableSelectionKeepsDisplayStringInStep() {
        String both = alpha.getName() + "," + beta.getName();
        sellerService.updateKitchen(kitchen.getId(), dto(both), seller);

        Kitchen reloaded = reload();
        assertThat(reloaded.getServedSocieties())
                .extracting(Society::getId).containsExactlyInAnyOrder(alpha.getId(), beta.getId());
        assertThat(reloaded.getServiceAreas())
                .as("display string is rebuilt from the persisted records")
                .isEqualTo(LocationService.toServiceAreaString(reloaded.getServedSocieties()));
        assertThat(societyRepository.findAll()).isNotEmpty();
    }
}
