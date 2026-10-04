package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Admin "Manage Areas &amp; Societies" master behind
 * {@code GET/POST/PATCH /api/admin/locations|areas|societies}.
 *
 * <p>Pins the rules the Admin screen depends on:</p>
 * <ul>
 *   <li>create / rename / enable-disable of Areas and Societies, with the
 *       validation the backend must enforce even when the client is bypassed;</li>
 *   <li>society names are unique per Area but may repeat under another Area;</li>
 *   <li>an Area with active Societies cannot be disabled first (order matters);</li>
 *   <li>rename and disable are non-destructive: existing buyer profiles,
 *       seller coverage and their eligibility keep resolving (regression
 *       scenario 3);</li>
 *   <li>a Society the Admin adds later never widens an existing seller's
 *       coverage (regression scenario 4);</li>
 *   <li>inactive records stay listed for the Admin so they can be re-enabled,
 *       while counts distinguish active from total.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-locations-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class AdminLocationsManagementTest {

    @Autowired private AdminService adminService;
    @Autowired private LocationService locationService;
    @Autowired private AreaRepository areaRepository;
    @Autowired private SocietyRepository societyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;

    private String sfx;
    private int mobileSeq;

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        mobileSeq = 0;
    }

    // ---------------- tree listing ----------------

    @Test
    void locationsListsCreatedAreasWithSocietiesAndCounts() {
        Map<String, Object> area = adminService.createArea("Alpha Area " + sfx);
        Map<String, Object> soc = adminService.createSociety((Long) area.get("id"), "Gamma Society " + sfx);

        Map<String, Object> tree = adminService.locations();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> areas = (List<Map<String, Object>>) tree.get("areas");
        Map<String, Object> row = areas.stream()
                .filter(a -> area.get("id").equals(a.get("id")))
                .findFirst().orElseThrow(() -> new AssertionError("created area missing from tree"));

        assertThat(row.get("active")).isEqualTo(true);
        assertThat(row.get("societyCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> societies = (List<Map<String, Object>>) row.get("societies");
        assertThat(societies).hasSize(1);
        assertThat(societies.get(0).get("name")).isEqualTo("Gamma Society " + sfx);
        assertThat(societies.get(0).get("active")).isEqualTo(true);
        assertThat(soc.get("areaId")).isEqualTo(area.get("id"));

        assertThat(((Number) tree.get("areaCount")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) tree.get("societyCount")).longValue())
                .isGreaterThanOrEqualTo(((Number) tree.get("activeSocietyCount")).longValue());
    }

    @Test
    void inactiveRecordsStayListedForReEnableButAreCountedSeparately() {
        Map<String, Object> area = adminService.createArea("Vis Area " + sfx);
        Long areaId = (Long) area.get("id");
        Map<String, Object> soc = adminService.createSociety(areaId, "Vis Society " + sfx);
        Long socId = (Long) soc.get("id");

        adminService.updateSociety(socId, null, false, null);

        Map<String, Object> tree = adminService.locations();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> areas = (List<Map<String, Object>>) tree.get("areas");
        Map<String, Object> row = areas.stream()
                .filter(a -> areaId.equals(a.get("id"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> societies = (List<Map<String, Object>>) row.get("societies");
        assertThat(societies).hasSize(1);
        assertThat(societies.get(0).get("active")).as("disabled society still listed for the Admin")
                .isEqualTo(false);
        assertThat(((Number) tree.get("activeSocietyCount")).longValue())
                .isLessThan(((Number) tree.get("societyCount")).longValue());
    }
    // ---------------- validation ----------------

    @Test
    void createAreaRejectsBlankAndDuplicateNames() {
        assertThatThrownBy(() -> adminService.createArea(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Area name");
        assertThatThrownBy(() -> adminService.createArea("   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Area name");
        adminService.createArea("Dup Area " + sfx);
        assertThatThrownBy(() -> adminService.createArea("DUP AREA " + sfx))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> adminService.createArea("x".repeat(161)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("160");
    }

    @Test
    void societyNamesAreUniquePerAreaButMayRepeatAcrossAreas() {
        Map<String, Object> areaA = adminService.createArea("Uni A " + sfx);
        Map<String, Object> areaB = adminService.createArea("Uni B " + sfx);
        String name = "Shared Name " + sfx;

        Map<String, Object> first = adminService.createSociety((Long) areaA.get("id"), name);
        Map<String, Object> second = adminService.createSociety((Long) areaB.get("id"), name);
        assertThat(second.get("id")).isNotEqualTo(first.get("id"));

        assertThatThrownBy(() -> adminService.createSociety((Long) areaA.get("id"), name.toUpperCase()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists under");
        assertThatThrownBy(() -> adminService.createSociety(null, name))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Area id");
        assertThatThrownBy(() -> adminService.createSociety((Long) areaA.get("id"), "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Society name");
    }

    @Test
    void renameKeepsIdentityAndPartialUpdatesLeaveAbsentFieldsUntouched() {
        Map<String, Object> area = adminService.createArea("Ren Area " + sfx);
        Long areaId = (Long) area.get("id");
        Map<String, Object> other = adminService.createArea("Ren Other " + sfx);

        Map<String, Object> renamed = adminService.updateArea(areaId, "Ren Area Renamed " + sfx, null, null);
        assertThat(renamed.get("id")).isEqualTo(areaId);
        assertThat(renamed.get("name")).isEqualTo("Ren Area Renamed " + sfx);
        // Absent fields are left alone: renaming with active=null must not disable.
        assertThat(renamed.get("active")).isEqualTo(true);

        // No-op update (both fields absent) is safe.
        Map<String, Object> noop = adminService.updateArea(areaId, null, null, null);
        assertThat(noop.get("name")).isEqualTo("Ren Area Renamed " + sfx);

        assertThatThrownBy(() -> adminService.updateArea(areaId, "ren other " + sfx, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        assertThat(other.get("id")).isNotEqualTo(areaId);
    }

    @Test
    void areaCannotBeDisabledWhileItStillHasActiveSocieties() {
        Map<String, Object> area = adminService.createArea("Gate Area " + sfx);
        Long areaId = (Long) area.get("id");
        Map<String, Object> soc = adminService.createSociety(areaId, "Gate Society " + sfx);
        Long socId = (Long) soc.get("id");

        assertThatThrownBy(() -> adminService.updateArea(areaId, null, false, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("still has active societies");

        // The required order: societies off first, then the area.
        adminService.updateSociety(socId, null, false, null);
        Map<String, Object> disabled = adminService.updateArea(areaId, null, false, null);
        assertThat(disabled.get("active")).isEqualTo(false);

        adminService.updateArea(areaId, null, true, null);
        adminService.updateSociety(socId, null, true, null);
        assertThat(adminService.locations().get("areas")).isNotNull();
    }
    // ---------------- regression 3: rename/disable keeps references working ----------------

    @Test
    void referencesSurviveRenameAndDisable() {
        Area area = locationService.createArea("Ref Area " + sfx);
        Society society = locationService.createSociety(area.getId(), "Ref Society " + sfx);

        User buyer = new User("Ref Buyer " + sfx, uniqueMobile(), "R-1", UserRole.BUYER);
        buyer.setSociety(society.getName());
        buyer.setSocietyRef(society);
        buyer.setAreaRef(area);
        buyer = userRepository.save(buyer);

        User seller = new User("Ref Seller " + sfx, uniqueMobile(), "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);
        Kitchen kitchen = kitchenRepository.save(
                new Kitchen("ref-k-" + sfx, "Ref Kitchen", "", null, seller));
        kitchen.setServiceAreas(society.getName());
        kitchen.setServedSocieties(new LinkedHashSet<>(Set.of(society)));
        kitchen = kitchenRepository.save(kitchen);

        Long areaId = area.getId();
        Long societyId = society.getId();

        // ---- rename both, IDs untouched ----
        adminService.updateArea(areaId, "Ref Area Renamed " + sfx, null, null);
        adminService.updateSociety(societyId, "Ref Society Renamed " + sfx, null, null);

        User buyerAfterRename = userRepository.findById(buyer.getId()).orElseThrow();
        assertThat(buyerAfterRename.getSocietyRef()).isNotNull();
        assertThat(buyerAfterRename.getSocietyRef().getId()).isEqualTo(societyId);
        Kitchen kitchenAfterRename = kitchenRepository.findById(kitchen.getId()).orElseThrow();
        assertThat(kitchenAfterRename.getServedSocieties())
                .extracting(Society::getId).containsExactly(societyId);
        assertThat(KitchenVisibility.isServiceAreaVisible(kitchenAfterRename, buyerAfterRename))
                .as("rename must not change historical eligibility")
                .isTrue();

        // ---- disable: NEW selections blocked, existing references keep working ----
        adminService.updateSociety(societyId, null, false, null);
        assertThatThrownBy(() -> locationService.requireSelectableLocation(areaId, societyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer available");

        Society societyAfter = societyRepository.findById(societyId).orElseThrow();
        assertThat(societyAfter.isActive()).isFalse();
        User buyerAfterDisable = userRepository.findById(buyer.getId()).orElseThrow();
        assertThat(buyerAfterDisable.getSocietyRef().getId()).isEqualTo(societyId);
        Kitchen kitchenAfterDisable = kitchenRepository.findById(kitchen.getId()).orElseThrow();
        assertThat(KitchenVisibility.isServiceAreaVisible(kitchenAfterDisable, buyerAfterDisable))
                .as("disabling keeps existing coverage and profiles resolving")
                .isTrue();
    }

    // ---------------- regression 4: a new Society never widens existing coverage ----------------

    @Test
    void newSocietyDoesNotExpandExistingSellerCoverage() {
        Area area = locationService.createArea("Grow Area " + sfx);
        Society first = locationService.createSociety(area.getId(), "Grow One " + sfx);

        User seller = new User("Grow Seller " + sfx, uniqueMobile(), "S-2", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);
        Kitchen kitchen = kitchenRepository.save(
                new Kitchen("grow-k-" + sfx, "Grow Kitchen", "", null, seller));
        kitchen.setServedSocieties(new LinkedHashSet<>(Set.of(first)));
        kitchen.setServiceAreas(first.getName());
        kitchen = kitchenRepository.save(kitchen);

        Society addedLater = locationService.createSociety(area.getId(), "Grow Two " + sfx);

        Kitchen reloaded = kitchenRepository.findById(kitchen.getId()).orElseThrow();
        assertThat(locationService.getSellerCoverage(reloaded))
                .as("the Admin's new Society is NOT adopted by existing coverage")
                .extracting(Society::getId).containsExactly(first.getId());

        User buyerInNew = new User("Grow Buyer " + sfx, uniqueMobile(), "B-2", UserRole.BUYER);
        buyerInNew.setSociety(addedLater.getName());
        buyerInNew.setSocietyRef(addedLater);
        buyerInNew.setAreaRef(area);
        assertThat(KitchenVisibility.isServiceAreaVisible(reloaded, buyerInNew))
                .as("buyer in the newly added Society is not eligible until the seller opts in")
                .isFalse();
    }

    private String uniqueMobile() {
        // 10 digits, unique per call: sfx is per-test, seq distinguishes users inside a test.
        int seed = (sfx.hashCode() & 0x7fffffff) + (++mobileSeq) * 7_919;
        return "9" + String.format("%09d", seed % 1_000_000_000);
    }
}
