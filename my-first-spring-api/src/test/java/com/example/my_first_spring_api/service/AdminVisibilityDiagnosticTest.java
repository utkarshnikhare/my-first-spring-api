package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.SellerType;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Admin Buyer &lt;-&gt; Kitchen visibility diagnostic.
 *
 * <p>This exists to answer "why can't this buyer see this kitchen?". The critical
 * property is that it must NOT become a second visibility engine: it calls the very
 * same {@link KitchenVisibility} predicates the buyer-facing endpoints call, and the
 * verdict it reports must equal the one those endpoints would produce. These tests
 * assert exactly that across every branch of the existing rules - ID-backed coverage,
 * both legacy fallbacks, paused kitchens, unapproved sellers and a Society-less buyer.
 * The screen is also read-only.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-diagnostic-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class AdminVisibilityDiagnosticTest {

    @Autowired private AdminService adminService;
    @Autowired private LocationService locationService;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private UserRepository userRepository;

    private String sfx;
    private int mobileSeq;
    private Area area;
    private Area otherArea;
    private Society served;
    private Society otherSociety;
    private User seller;
    private Kitchen idCoveredKitchen;

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        mobileSeq = 0;
        area = locationService.createArea("Diag Area " + sfx);
        otherArea = locationService.createArea("Diag Other " + sfx);
        served = locationService.createSociety(area.getId(), "Diag Society " + sfx);
        otherSociety = locationService.createSociety(otherArea.getId(), "Diag Other Society " + sfx);

        seller = new User("Diag Seller " + sfx, uniqueMobile(), "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);

        idCoveredKitchen = kitchen("covered-" + sfx, "Diag Covered", true);
        idCoveredKitchen.setServedSocieties(new LinkedHashSet<>(Set.of(served)));
        idCoveredKitchen = kitchenRepository.save(idCoveredKitchen);
    }

    private String uniqueMobile() {
        int seed = (sfx.hashCode() & 0x7fffffff) + (++mobileSeq) * 6_013;
        return "8" + String.format("%09d", seed % 1_000_000_000);
    }

    private Kitchen kitchen(String key, String name, boolean availableToday) {
        Kitchen k = new Kitchen(key, name, "", null, seller);
        k.setAvailableToday(availableToday);
        k.setSellerType(SellerType.KITCHEN);
        return k;
    }

    private User buyerWith(Society society) {
        User b = new User("Diag Buyer " + sfx + " " + (++mobileSeq), uniqueMobile(), null, UserRole.BUYER);
        b.setBuilding("A");
        b.setFlatHouseNumber("101");
        if (society != null) {
            b.setSociety(society.getName());
            b.setSocietyRef(society);
            b.setAreaRef(society.getArea());
        }
        return userRepository.save(b);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resultsFor(Long buyerId) {
        return (List<Map<String, Object>>) adminService.visibilityDiagnostic(buyerId, null).get("results");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowFor(Long buyerId, Long kitchenId) {
        List<Map<String, Object>> results =
                (List<Map<String, Object>>) adminService.visibilityDiagnostic(buyerId, kitchenId).get("results");
        assertThat(results).hasSize(1);
        return results.get(0);
    }
    // ---------- the diagnostic must agree with the real visibility engine ----------

    @Test
    void theReportedVerdictEqualsTheExistingVisibilityEngine() {
        User matching = buyerWith(served);
        Map<String, Object> row = rowFor(matching.getId(), idCoveredKitchen.getId());

        boolean engineSaysVisible = KitchenVisibility.isServiceAreaVisible(idCoveredKitchen, matching)
                && KitchenVisibility.isPubliclyVisible(idCoveredKitchen)
                && !KitchenVisibility.isPaused(idCoveredKitchen);
        assertThat(row.get("visible"))
                .as("must equal KitchenVisibility's own answer, not a re-derived one")
                .isEqualTo(engineSaysVisible);
        assertThat((Boolean) row.get("visible")).isTrue();
    }

    @Test
    void aMatchingSocietyIsReportedVisibleWithACoverageMatchReason() {
        Map<String, Object> row = rowFor(buyerWith(served).getId(), idCoveredKitchen.getId());

        assertThat((Boolean) row.get("visible")).isTrue();
        assertThat(row.get("coverageMode")).isEqualTo("ID-backed coverage");
        assertThat((List<String>) row.get("kitchenCoverage")).containsExactly(served.getName());
        assertThat((List<String>) row.get("reasons")).anyMatch(r -> r.contains("Coverage match"));
    }

    @Test
    void aDifferentSocietyIsReportedBlockedWithAFactualReason() {
        Map<String, Object> row = rowFor(buyerWith(otherSociety).getId(), idCoveredKitchen.getId());

        assertThat((Boolean) row.get("visible")).isFalse();
        assertThat((List<String>) row.get("reasons"))
                .anyMatch(r -> r.contains("does not include the buyer's Society"));
    }

    @Test
    void aPausedKitchenIsReportedBlockedAndExplained() {
        Kitchen paused = kitchen("paused-" + sfx, "Diag Paused", false);
        paused.setServedSocieties(new LinkedHashSet<>(Set.of(served)));
        paused = kitchenRepository.save(paused);

        Map<String, Object> row = rowFor(buyerWith(served).getId(), paused.getId());

        assertThat((Boolean) row.get("paused")).isTrue();
        assertThat((Boolean) row.get("visible")).isFalse();
        assertThat((List<String>) row.get("reasons")).anyMatch(r -> r.contains("paused"));
    }

    @Test
    void anUnapprovedSellerIsReportedBlockedAndExplained() {
        User pending = new User("Diag Pending " + sfx, uniqueMobile(), "S-2", UserRole.SELLER);
        pending.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        pending = userRepository.save(pending);

        Kitchen k = new Kitchen("pending-" + sfx, "Diag Pending Kitchen", "", null, pending);
        k.setAvailableToday(true);
        k.setSellerType(SellerType.KITCHEN);
        k.setServedSocieties(new LinkedHashSet<>(Set.of(served)));
        k = kitchenRepository.save(k);

        Map<String, Object> row = rowFor(buyerWith(served).getId(), k.getId());

        assertThat((Boolean) row.get("publiclyVisible")).isFalse();
        assertThat((Boolean) row.get("visible")).isFalse();
        assertThat((List<String>) row.get("reasons")).anyMatch(r -> r.contains("not approved"));
    }
    @Test
    void aBuyerWithNoSocietyIsExplainedRatherThanGuessed() {
        User noSociety = buyerWith(null);
        Map<String, Object> row = rowFor(noSociety.getId(), idCoveredKitchen.getId());

        assertThat((Boolean) row.get("visible")).isFalse();
        assertThat((List<String>) row.get("reasons"))
                .anyMatch(r -> r.contains("has not selected a Society yet"));

        @SuppressWarnings("unchecked")
        Map<String, Object> buyer = (Map<String, Object>)
                adminService.visibilityDiagnostic(noSociety.getId(), null).get("buyer");
        assertThat(buyer.get("profileComplete")).isEqualTo(false);
        assertThat((List<String>) buyer.get("profileMissing")).contains("society");
    }

    @Test
    void legacyCoveragePathsAreLabelledRatherThanRewritten() {
        Kitchen legacyAreas = kitchen("legacy-areas-" + sfx, "Diag Legacy Areas", true);
        legacyAreas.setServiceAreas(served.getName());
        legacyAreas = kitchenRepository.save(legacyAreas);

        Map<String, Object> areasRow = rowFor(buyerWith(served).getId(), legacyAreas.getId());
        assertThat(areasRow.get("coverageMode")).isEqualTo("Legacy service-areas string");
        assertThat((Boolean) areasRow.get("visible")).isTrue();

        Kitchen legacySociety = kitchen("legacy-soc-" + sfx, "Diag Legacy Society", true);
        legacySociety.setSociety(served.getName());
        legacySociety = kitchenRepository.save(legacySociety);

        Map<String, Object> societyRow = rowFor(buyerWith(served).getId(), legacySociety.getId());
        assertThat(societyRow.get("coverageMode")).isEqualTo("Legacy kitchen society");
        assertThat((Boolean) societyRow.get("visible")).isTrue();

        Kitchen bare = kitchenRepository.save(kitchen("bare-" + sfx, "Diag Bare", true));
        assertThat(rowFor(buyerWith(served).getId(), bare.getId()).get("coverageMode"))
                .isEqualTo("No coverage configured");
    }

    // ---------- summary + read-only guarantees ----------

    @Test
    @SuppressWarnings("unchecked")
    void theSummaryCountsVisibleAndBlockedKitchens() {
        kitchenRepository.save(kitchen("blocked-" + sfx, "Diag Blocked", true));
        Long buyerId = buyerWith(served).getId();

        Map<String, Object> summary =
                (Map<String, Object>) adminService.visibilityDiagnostic(buyerId, null).get("summary");
        List<Map<String, Object>> results = resultsFor(buyerId);
        int visible = (int) results.stream().filter(r -> Boolean.TRUE.equals(r.get("visible"))).count();

        assertThat(summary.get("totalKitchens")).isEqualTo(results.size());
        assertThat(summary.get("visible")).isEqualTo(visible);
        assertThat(summary.get("blocked")).isEqualTo(results.size() - visible);
        assertThat((Integer) summary.get("blocked")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void runningTheDiagnosticChangesNoData() {
        Long buyerId = buyerWith(served).getId();
        String serviceAreasBefore = idCoveredKitchen.getServiceAreas();

        adminService.visibilityDiagnostic(buyerId, null);

        Kitchen after = kitchenRepository.findById(idCoveredKitchen.getId()).orElseThrow();
        assertThat(after.getServedSocieties()).extracting(Society::getId).containsExactly(served.getId());
        assertThat(after.getServiceAreas()).isEqualTo(serviceAreasBefore);
        assertThat(after.getAvailableToday()).isTrue();
    }

    @Test
    void anUnknownBuyerIsRejectedRatherThanSilentlyEmpty() {
        assertThatThrownBy(() -> adminService.visibilityDiagnostic(999999L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownKitchenIsRejected() {
        Long buyerId = buyerWith(served).getId();
        assertThatThrownBy(() -> adminService.visibilityDiagnostic(buyerId, 999999L))
                .isInstanceOf(com.example.my_first_spring_api.exception.KitchenNotFoundException.class);
    }
    // ANCHOR-T
}
