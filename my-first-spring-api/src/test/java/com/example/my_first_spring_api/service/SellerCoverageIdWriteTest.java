package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Area-driven, Society-ID coverage write path used by the seller picker and the
 * Admin kitchen editor.
 *
 * <p>Covers: reading the active Area/Society choices, persisting the seller's
 * COMPLETE selection as IDs, replacing coverage on update, honouring a manual
 * deselection, refusing invalid/inactive/cross-area IDs, and leaving an unrelated
 * profile edit (one that sends no coverage fields) untouched. The pre-existing
 * name-string callers keep working through the same shared service.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:coverage-id-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SellerCoverageIdWriteTest {

    @Autowired private LocationService locationService;
    @Autowired private SellerService sellerService;
    @Autowired private AdminService adminService;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SocietyRepository societyRepository;

    private String sfx;
    private int mobileSeq;
    private Area area;
    private Area otherArea;
    private Society alpha;
    private Society beta;
    private Society gamma;
    private Society foreign;
    private User seller;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        mobileSeq = 0;
        area = locationService.createArea("Cov Area " + sfx);
        alpha = locationService.createSociety(area.getId(), "Cov Alpha " + sfx);
        beta = locationService.createSociety(area.getId(), "Cov Beta " + sfx);
        gamma = locationService.createSociety(area.getId(), "Cov Gamma " + sfx);
        otherArea = locationService.createArea("Cov Other " + sfx);
        foreign = locationService.createSociety(otherArea.getId(), "Cov Foreign " + sfx);

        seller = new User("Cov Seller " + sfx, uniqueMobile(), "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);

        kitchen = kitchenRepository.save(new Kitchen("cov-k-" + sfx, "Cov Kitchen", "", null, seller));
    }

    private String uniqueMobile() {
        int seed = (sfx.hashCode() & 0x7fffffff) + (++mobileSeq) * 7_919;
        return "8" + String.format("%09d", seed % 1_000_000_000);
    }

    private KitchenUpdateDto idDto(Long areaId, List<Long> ids) {
        KitchenUpdateDto d = new KitchenUpdateDto();
        d.setAreaId(areaId);
        d.setSocietyIds(ids);
        return d;
    }

    private Kitchen reload() {
        return kitchenRepository.findById(kitchen.getId()).orElseThrow();
    }

    private Set<Long> coveredIds() {
        Set<Long> ids = new LinkedHashSet<>();
        for (Society s : reload().getServedSocieties()) ids.add(s.getId());
        return ids;
    }
    // ---------------- 1 + 2: the choices offered to the picker ----------------

    @Test
    void coverageOptionsExposeActiveAreasWithTheirActiveSocietiesAndIds() {
        List<CoverageOptionDto> options = locationService.getCoverageOptions();

        CoverageOptionDto mine = options.stream()
                .filter(a -> area.getId().equals(a.getId()))
                .findFirst().orElseThrow(() -> new AssertionError("new area missing from coverage options"));
        assertThat(mine.getName()).isEqualTo(area.getName());
        assertThat(mine.getSocieties())
                .extracting(CoverageOptionDto.SocietyOptionDto::getId)
                .containsExactlyInAnyOrder(alpha.getId(), beta.getId(), gamma.getId());
        assertThat(mine.getSocieties())
                .extracting(CoverageOptionDto.SocietyOptionDto::getName)
                .contains(alpha.getName(), beta.getName(), gamma.getName());
    }

    @Test
    void coverageOptionsOmitInactiveAreasAndInactiveSocieties() {
        locationService.setSocietyActive(gamma.getId(), false);
        Area disabledArea = locationService.createArea("Cov Disabled " + sfx);
        Society hidden = locationService.createSociety(disabledArea.getId(), "Cov Hidden " + sfx);
        // An Area can only be disabled once its societies are, so do that first.
        locationService.setSocietyActive(hidden.getId(), false);
        locationService.setAreaActive(disabledArea.getId(), false);

        List<CoverageOptionDto> options = locationService.getCoverageOptions();

        assertThat(options).extracting(CoverageOptionDto::getId).doesNotContain(disabledArea.getId());
        CoverageOptionDto mine = options.stream()
                .filter(a -> area.getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(mine.getSocieties()).extracting(CoverageOptionDto.SocietyOptionDto::getId)
                .containsExactlyInAnyOrder(alpha.getId(), beta.getId());
    }

    // ---------------- 3 + 4 + 5: seller update replaces coverage by ID ----------------

    @Test
    void sellerUpdatePersistsTheCompleteSelectionAsSocietyIds() {
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId(), beta.getId())), seller);

        assertThat(coveredIds()).containsExactlyInAnyOrder(alpha.getId(), beta.getId());
        Kitchen after = reload();
        assertThat(after.getServiceAreas()).contains(alpha.getName()).contains(beta.getName());
        assertThat(after.getServiceAreas()).doesNotContain(gamma.getName());
    }

    @Test
    void deselectingASocietyRemovesItFromCoverage() {
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId(), beta.getId())), seller);
        assertThat(coveredIds()).contains(beta.getId());

        // The seller unticks beta and saves again.
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId())), seller);

        assertThat(coveredIds()).containsExactly(alpha.getId());
        assertThat(reload().getServiceAreas()).doesNotContain(beta.getName());
    }

    @Test
    void reselectingAfterADeselectionRestoresCoverage() {
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId())), seller);
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId(), beta.getId())), seller);

        assertThat(coveredIds()).containsExactlyInAnyOrder(alpha.getId(), beta.getId());
    }

    @Test
    void anEmptySelectionClearsCoverageDeliberately() {
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId())), seller);

        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of()), seller);

        assertThat(coveredIds()).isEmpty();
        assertThat(reload().getServiceAreas()).isEmpty();
    }

    @Test
    void duplicateSocietyIdsAreHarmless() {
        sellerService.updateKitchen(kitchen.getId(),
                idDto(area.getId(), List.of(alpha.getId(), alpha.getId(), beta.getId())), seller);

        assertThat(coveredIds()).containsExactlyInAnyOrder(alpha.getId(), beta.getId());
    }
    // ---------------- 6: a later society is never silently adopted ----------------

    @Test
    void aSocietyCreatedLaterIsNotSilentlyAddedToExistingCoverage() {
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId())), seller);
        Society later = locationService.createSociety(area.getId(), "Cov Later " + sfx);

        // Re-saving the SAME complete selection must not pick the new society up.
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId())), seller);

        assertThat(coveredIds()).containsExactly(alpha.getId());
        assertThat(coveredIds()).doesNotContain(later.getId());
        // It only becomes selectable on the next explicit selection.
        assertThat(locationService.getCoverageOptions().stream()
                .filter(a -> area.getId().equals(a.getId())).findFirst().orElseThrow()
                .getSocieties()).extracting(CoverageOptionDto.SocietyOptionDto::getId).contains(later.getId());
    }

    // ---------------- 7 + 8 + 9: validation ----------------

    @Test
    void anUnknownSocietyIdIsRejected() {
        assertThatThrownBy(() -> sellerService.updateKitchen(kitchen.getId(),
                idDto(area.getId(), List.of(999_999_999L)), seller))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInactiveSocietyCannotBeNewlySelected() {
        locationService.setSocietyActive(beta.getId(), false);

        assertThatThrownBy(() -> sellerService.updateKitchen(kitchen.getId(),
                idDto(area.getId(), List.of(beta.getId())), seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(beta.getName());
    }

    @Test
    void aSocietyFromAnotherAreaIsRejected() {
        assertThatThrownBy(() -> sellerService.updateKitchen(kitchen.getId(),
                idDto(area.getId(), List.of(foreign.getId())), seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the selected area");
    }

    @Test
    void aMissingAreaIsRejected() {
        assertThatThrownBy(() -> sellerService.updateKitchen(kitchen.getId(),
                idDto(null, List.of(alpha.getId())), seller))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRejectedSelectionLeavesExistingCoverageIntact() {
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId())), seller);

        assertThatThrownBy(() -> sellerService.updateKitchen(kitchen.getId(),
                idDto(area.getId(), List.of(alpha.getId(), foreign.getId())), seller))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(coveredIds()).containsExactly(alpha.getId());
    }
    // ---------------- 3: seller create persists IDs ----------------

    @Test
    void sellerCreatePersistsTheSelectedSocietyIds() {
        KitchenCreateDto create = new KitchenCreateDto();
        create.setName("covcreate-" + sfx);
        create.setDisplayName("Cov Create Kitchen");
        create.setSociety(alpha.getName());
        create.setAreaId(area.getId());
        create.setSocietyIds(List.of(alpha.getId(), beta.getId()));

        KitchenDto created = sellerService.createKitchen(create, seller);

        Kitchen saved = kitchenRepository.findById(created.getId()).orElseThrow();
        Set<Long> ids = new LinkedHashSet<>();
        for (Society s : saved.getServedSocieties()) ids.add(s.getId());
        assertThat(ids).containsExactlyInAnyOrder(alpha.getId(), beta.getId());
        assertThat(saved.getServiceAreas()).contains(alpha.getName()).contains(beta.getName());
        // The DTO echoes the authoritative IDs so the picker can restore the selection.
        assertThat(created.getServedSocietyIds()).containsExactlyInAnyOrder(alpha.getId(), beta.getId());
    }

    @Test
    void sellerCreateWithoutAnAreaFallsBackToTheLegacyNamePath() {
        KitchenCreateDto create = new KitchenCreateDto();
        create.setName("covlegacy-" + sfx);
        create.setDisplayName("Cov Legacy Kitchen");
        create.setSociety(beta.getName());
        create.setServiceAreas(beta.getName());

        KitchenDto created = sellerService.createKitchen(create, seller);

        Kitchen saved = kitchenRepository.findById(created.getId()).orElseThrow();
        assertThat(saved.getServedSocieties()).extracting(Society::getId).containsExactly(beta.getId());
    }

    // ---------------- 10: the admin editor uses the same ID path ----------------

    @Test
    void adminCoverageUpdateUsesSocietyIds() {
        Map<String, Object> out = adminService.updateKitchenServiceAreas(kitchen.getId(), null,
                area.getId(), List.of(alpha.getId(), gamma.getId()));

        assertThat(coveredIds()).containsExactlyInAnyOrder(alpha.getId(), gamma.getId());
        assertThat(out.get("servedSocietyIds")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(Long.class))
                .containsExactlyInAnyOrder(alpha.getId(), gamma.getId());
        assertThat((String) out.get("serviceAreas")).contains(alpha.getName()).contains(gamma.getName());
    }

    @Test
    void adminCoverageUpdateRejectsACrossAreaSociety() {
        assertThatThrownBy(() -> adminService.updateKitchenServiceAreas(kitchen.getId(), null,
                area.getId(), List.of(foreign.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------- compatibility + no-op semantics ----------------

    @Test
    void anEditThatSendsNoCoverageFieldsLeavesCoverageUntouched() {
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId(), beta.getId())), seller);

        KitchenUpdateDto profileOnly = new KitchenUpdateDto();
        profileOnly.setDescription("Just a description change");
        sellerService.updateKitchen(kitchen.getId(), profileOnly, seller);

        assertThat(coveredIds()).containsExactlyInAnyOrder(alpha.getId(), beta.getId());
        assertThat(reload().getDescription()).isEqualTo("Just a description change");
    }

    @Test
    void theLegacyNameStringPathStillWorksAndStaysSynchronised() {
        KitchenUpdateDto legacy = new KitchenUpdateDto();
        legacy.setServiceAreas(beta.getName());
        sellerService.updateKitchen(kitchen.getId(), legacy, seller);

        assertThat(coveredIds()).containsExactly(beta.getId());
        assertThat(reload().getServiceAreas()).isEqualTo(beta.getName());
    }

    @Test
    void coverageDrivesKitchenVisibilityBySocietyId() {
        sellerService.updateKitchen(kitchen.getId(), idDto(area.getId(), List.of(alpha.getId())), seller);

        User buyerInBeta = new User("Cov Buyer " + sfx, uniqueMobile(), "B-1", UserRole.BUYER);
        buyerInBeta.setSociety(beta.getName());
        buyerInBeta.setSocietyRef(beta);
        buyerInBeta.setAreaRef(area);
        userRepository.save(buyerInBeta);

        assertThat(KitchenVisibility.isServiceAreaVisible(reload(), buyerInBeta))
                .as("a buyer outside the selected societies is not eligible")
                .isFalse();
    }
}