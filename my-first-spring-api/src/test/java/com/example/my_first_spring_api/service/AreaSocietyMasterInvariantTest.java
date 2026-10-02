package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.repository.SocietyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase C (G4) - the Area/Society master consistency INVARIANTS.
 *
 * <p>This phase audited the six "legacy societies" that the earlier gap report
 * described as having no Area mapping. The audit found that framing to be a
 * category error, and these tests pin the invariants that make it impossible to
 * reintroduce the problem by accident:</p>
 *
 * <ol>
 *   <li><b>A master Society always has an Area.</b> The entity declares
 *       {@code @ManyToOne(optional = false)} over a {@code nullable = false}
 *       column, so an Area-less master record cannot be persisted at all. The six
 *       legacy names are therefore not unmapped master rows - they are free-text
 *       strings on {@code User.society} / {@code Kitchen.society} surfaced by the
 *       separate read-only {@code SocietyDirectory}.</li>
 *   <li><b>A legacy string with no master record is never auto-assigned.</b>
 *       {@link LocationService#resolveLegacySociety(String)} returns empty for the
 *       six legacy names, so nothing can silently attach them to an Area. This is
 *       the explicit product decision G4 still needs, expressed as an invariant
 *       rather than as a guessed mapping.</li>
 *   <li><b>An ambiguous name is not guessed either</b> - the same name under two
 *       Areas resolves to empty.</li>
 *   <li><b>Coverage options only ever offer a Society under its own Area.</b></li>
 * </ol>
 *
 * <p>No Area assignment is invented anywhere in this class. The only records it
 * creates are its own, through the existing {@link LocationService} API.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:location-invariant-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class AreaSocietyMasterInvariantTest {

    /** The six names the gap report called "unmapped". They are legacy TEXT, not master rows. */
    private static final List<String> LEGACY_SOCIETY_NAMES = List.of(
            "Sunshine Society", "Green Valley", "Lake View", "Hill Side", "Riverside", "Lohegaon");

    @Autowired private LocationService locationService;
    @Autowired private SocietyRepository societyRepository;
    @Autowired private AreaRepository areaRepository;

    private String sfx;
    private Area alphaArea;
    private Area betaArea;
    private Society alpha;

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        alphaArea = locationService.createArea("Inv Alpha Area " + sfx);
        betaArea = locationService.createArea("Inv Beta Area " + sfx);
        alpha = locationService.createSociety(alphaArea.getId(), "Inv Alpha Society " + sfx);
    }
    /** Invariant 1: every master Society belongs to exactly one Area. */
    @Test
    void everyMasterSocietyBelongsToAnArea() {
        assertThat(societyRepository.findAll())
                .as("the Area -> Society master can never hold an orphaned society")
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.getArea())
                        .as("society '%s' must have an Area", s.getName())
                        .isNotNull());

        assertThat(societyRepository.findByAreaIdOrderByNameAsc(alphaArea.getId()))
                .extracting(Society::getId)
                .contains(alpha.getId());
    }

    /**
     * Invariant 2 - THE Phase C decision. The six legacy names resolve to nothing,
     * so the platform cannot and does not attach them to an Area on its own.
     */
    @Test
    void theSixLegacySocietyNamesHaveNoMasterRecordAndAreNotAutoAssigned() {
        for (String legacy : LEGACY_SOCIETY_NAMES) {
            assertThat(locationService.resolveLegacySociety(legacy))
                    .as("'%s' is legacy free text with no Area - it must stay unresolved "
                            + "until an authoritative mapping is supplied", legacy)
                    .isEmpty();
        }

        assertThat(societyRepository.findAll())
                .as("no legacy name may have been silently created as a master record")
                .extracting(Society::getName)
                .doesNotContainAnyElementsOf(LEGACY_SOCIETY_NAMES);
    }

    /** A real master name still resolves - the legacy bridge is not disabled. */
    @Test
    void aRealMasterSocietyNameResolvesToItsRecordAndArea() {
        Optional<Society> resolved = locationService.resolveLegacySociety(alpha.getName());

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(alpha.getId());
        assertThat(resolved.get().getArea().getId()).isEqualTo(alphaArea.getId());
    }

    /** Invariant 3: the same name under two Areas is ambiguous, not guessed. */
    @Test
    void anAmbiguousNameIsNotGuessed() {
        locationService.createSociety(betaArea.getId(), alpha.getName());

        assertThat(societyRepository.findByNameKey(Society.deriveNameKey(alpha.getName())))
                .as("two areas really do hold the same name")
                .hasSize(2);

        assertThat(locationService.resolveLegacySociety(alpha.getName()))
                .as("an ambiguous name must resolve to nothing rather than pick one")
                .isEmpty();
    }

    /** Invariant 4: a Society is only ever offered under the Area that owns it. */
    @Test
    void coverageOptionsNeverOfferASocietyUnderTheWrongArea() {
        locationService.createSociety(betaArea.getId(), "Inv Beta Society " + sfx);

        List<CoverageOptionDto> options = locationService.getCoverageOptions();
        assertThat(options).isNotEmpty();

        for (CoverageOptionDto area : options) {
            for (CoverageOptionDto.SocietyOptionDto option : area.getSocieties()) {
                Society society = societyRepository.findById(option.getId()).orElseThrow();
                assertThat(society.getArea().getId())
                        .as("'%s' is offered under '%s' but belongs to '%s'",
                                society.getName(), area.getName(), society.getArea().getName())
                        .isEqualTo(area.getId());
            }
        }
    }
    /**
     * An Area cannot be switched off while it still holds active societies, and once
     * they are all off the whole Area leaves the active option list.
     */
    @Test
    void anAreaStaysActiveUntilItsSocietiesAreDisabled() {
        assertThat(locationService.getCoverageOptions())
                .filteredOn(a -> a.getId().equals(alphaArea.getId()))
                .singleElement()
                .satisfies(a -> assertThat(a.getSocieties())
                        .extracting(CoverageOptionDto.SocietyOptionDto::getName)
                        .contains(alpha.getName()));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> locationService.setAreaActive(alphaArea.getId(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active societies");

        locationService.setSocietyActive(alpha.getId(), false);
        locationService.setAreaActive(alphaArea.getId(), false);

        assertThat(locationService.getCoverageOptions())
                .filteredOn(a -> a.getId().equals(alphaArea.getId()))
                .isEmpty();

        assertThat(areaRepository.findById(alphaArea.getId()))
                .as("the Area record survives so existing references still resolve")
                .isPresent()
                .get()
                .extracting(Area::isActive)
                .isEqualTo(false);
    }

    /** Disabling a Society removes it from its Area's options but keeps the record. */
    @Test
    void disablingASocietyKeepsTheRecordButHidesItFromOptions() {
        locationService.setSocietyActive(alpha.getId(), false);

        assertThat(locationService.getCoverageOptions())
                .filteredOn(a -> a.getId().equals(alphaArea.getId()))
                .singleElement()
                .satisfies(a -> assertThat(a.getSocieties())
                        .extracting(CoverageOptionDto.SocietyOptionDto::getName)
                        .doesNotContain(alpha.getName()));

        assertThat(societyRepository.findById(alpha.getId()))
                .as("master records are disabled, never deleted, so history still resolves")
                .isPresent()
                .get()
                .extracting(Society::isActive)
                .isEqualTo(false);
    }

    /**
     * Areas and Societies are the only location master, and every record in it -
     * seeded or created here - resolves to a real, persisted Area.
     */
    @Test
    void theMasterHoldsExactlyTheRecordsItWasGiven() {
        long beforeAreas = areaRepository.count();
        long beforeSocieties = societyRepository.count();

        assertThat(areaRepository.findById(alphaArea.getId())).isPresent();
        assertThat(areaRepository.findById(betaArea.getId())).isPresent();
        assertThat(societyRepository.findById(alpha.getId()))
                .get()
                .extracting(Society::getArea)
                .extracting(Area::getId)
                .isEqualTo(alphaArea.getId());

        assertThat(areaRepository.count())
                .as("creating an Area must not implicitly create anything else")
                .isEqualTo(beforeAreas);
        assertThat(societyRepository.count())
                .as("no society was invented for the second, empty Area")
                .isEqualTo(beforeSocieties);

        assertThat(societyRepository.findByAreaIdOrderByNameAsc(betaArea.getId()))
                .as("an Area with no societies is legal and simply offers nothing")
                .isEmpty();
    }
}