package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.exception.KitchenNotEligibleException;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.AdminAuditLog;
import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.AdminAuditLogRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.KitchenSlugAliasRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Public URL (slug) lifecycle end-to-end over REAL persistence: generation,
 * race-safe reservation, permanence, alias redirects, status-based visibility
 * of the clean {@code /kitchen/{slug}} route, seller immutability and Admin URL
 * management with audit.
 *
 * <p>No service is mocked - every step runs through the production
 * {@link SlugService}, {@link SellerService}, {@link KitchenService} and
 * {@link AdminService} against a real H2 database, so the assertions describe
 * what the application actually persists.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kitchen-public-slug;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class KitchenPublicSlugTest {

    @Autowired private SlugService slugService;
    @Autowired private SellerService sellerService;
    @Autowired private KitchenService kitchenService;
    @Autowired private AdminService adminService;
    @Autowired private LocationService locationService;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private KitchenSlugAliasRepository aliasRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AdminAuditLogRepository auditRepo;

    /** Static so every test in this class gets a unique mobile (the column is UNIQUE). */
    private static int mobileSeq = 0;

    private static synchronized String nextMobile() {
        return "9" + String.format("%08d", 20000000 + (++mobileSeq));
    }

    private String tag;
    private Area area;
    private Society alpha;
    private Society beta;
    private User seller;
    private Kitchen kitchen;
    private String slug;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        area = locationService.createArea("Slug Area " + tag);
        alpha = locationService.createSociety(area.getId(), "Slug Alpha " + tag);
        beta = locationService.createSociety(area.getId(), "Slug Beta " + tag);

        seller = approvedSeller("Fix-" + tag);
        kitchen = createKitchen(seller, "slug-kitchen-" + tag, List.of(alpha.getId()));
        slug = kitchen.getSlug();
    }

    // ---------------- fixtures ----------------

    private User approvedSeller(String nameTag) {
        User s = new User("Slug Seller " + nameTag, nextMobile(), "S-1", UserRole.SELLER);
        s.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return userRepository.save(s);
    }

    /** A kitchen created through the real registration path (slug reserved first). */
    private Kitchen createKitchen(User owner, String desiredSlug, List<Long> societyIds) {
        KitchenCreateDto dto = new KitchenCreateDto();
        dto.setName(desiredSlug);
        dto.setDisplayName("Slug Test Kitchen " + desiredSlug);
        dto.setDescription("Public URL lifecycle test");
        dto.setAvailableToday(Boolean.TRUE);
        if (societyIds != null && !societyIds.isEmpty()) {
            dto.setAreaId(area.getId());
            dto.setSocietyIds(societyIds);
        }
        Long id = sellerService.createKitchen(dto, owner).getId();
        return kitchenRepository.findById(id).orElseThrow();
    }

    private Kitchen reload() {
        return kitchenRepository.findById(kitchen.getId()).orElseThrow();
    }

    private User buyerIn(Society society) {
        User b = new User("Slug Buyer " + tag, nextMobile(), "A-101", UserRole.BUYER);
        b.setSociety(society.getName());
        b.setSocietyRef(society);
        b.setAreaRef(society.getArea());
        b.setBuilding("B-1");
        return userRepository.save(b);
    }

    private User admin() {
        User a = new User("Slug Admin " + tag, nextMobile(), "A-1", UserRole.ADMIN);
        return userRepository.save(a);
    }

    // ---------------- generation ----------------

    @Test
    @DisplayName("slugify normalises unsafe names into stable URL slugs")
    void slugifyNormalisesNamesIntoStableSlugs() {
        assertThat(SlugService.slugify("Aarti's Cloud Kitchen")).isEqualTo("aartis-cloud-kitchen");
        assertThat(SlugService.slugify("  Cr\u00e8me   Br\u00fbl\u00e9e!!  ")).isEqualTo("creme-brulee");
        assertThat(SlugService.slugify("Multi Word\tName")).isEqualTo("multi-word-name");
        assertThat(SlugService.slugify(null)).isEmpty();
        assertThat(SlugService.slugify("---")).isEmpty();
        assertThat(SlugService.slugify("!!! ' '''")).isEmpty();
    }

    @Test
    @DisplayName("registration reserves the slug server-side and keeps the legacy name column in step")
    void createKitchenReservesSlugAndKeepsLegacyNameInStep() {
        assertThat(slug).isEqualTo("slug-kitchen-" + tag);
        assertThat(kitchen.getName()).isEqualTo(slug);
        assertThat(kitchen.publicSlug()).isEqualTo(slug);
    }

    @Test
    @DisplayName("a kitchen saved without an explicit slug backfills it from the name on persist")
    void persistWithoutExplicitSlugBackfillsFromName() {
        Kitchen legacy = new Kitchen("Backfill Name " + tag, "Backfill Display", "", null, seller);
        assertThat(legacy.getSlug()).as("no slug set by the constructor").isBlank();

        kitchenRepository.saveAndFlush(legacy);

        assertThat(kitchenRepository.findById(legacy.getId()).orElseThrow().getSlug())
                .isEqualTo("backfill-name-" + tag);
    }

    // ---------------- uniqueness / races ----------------

    @Test
    @DisplayName("a duplicate desired slug gets the next -2 suffix instead of failing")
    void duplicateDesiredSlugGetsNextSuffix() {
        User other = approvedSeller("Dup-" + tag);
        Kitchen second = createKitchen(other, "slug-kitchen-" + tag, null);

        assertThat(second.getSlug()).isEqualTo("slug-kitchen-" + tag + "-2");
        assertThat(second.getSlug()).isNotEqualTo(slug);
    }

    @Test
    @DisplayName("the suggestion logic suffixes against slugs AND retired aliases")
    void retiredSlugStaysTakenAndSuggestsSuffix() {
        // Retire `slug` by changing it, then the old value must never be reusable.
        adminService.changeKitchenSlug(kitchen.getId(), "renamed-" + tag, admin());

        assertThat(slugService.isTaken(slug)).isTrue();
        assertThat(slugService.suggestUnique(slug)).isEqualTo(slug + "-2");
        assertThat(slugService.suggestUnique("Fresh Base " + tag)).isEqualTo("fresh-base-" + tag);
    }

    @Test
    @DisplayName("the database unique constraint is the hard guard: a duplicate slug INSERT is rejected")
    void databaseUniqueConstraintRejectsDuplicateSlug() {
        Kitchen first = new Kitchen("Race A " + tag, "Race A", "", null, seller);
        first.setSlug("dup-guard-" + tag);
        kitchenRepository.saveAndFlush(first);

        Kitchen second = new Kitchen("Race B " + tag, "Race B", "", null, seller);
        second.setSlug("dup-guard-" + tag);

        assertThatThrownBy(() -> kitchenRepository.saveAndFlush(second))
                .as("the DB - not the browser - is the uniqueness authority")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("two simultaneous registrations can never claim the same slug")
    void concurrentRegistrationsNeverShareASlug() throws Exception {
        User first = approvedSeller("Race1-" + tag);
        User second = approvedSeller("Race2-" + tag);
        String desired = "race-" + tag;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> attemptFirst = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return createKitchen(first, desired, null).getSlug();
        };
        Callable<String> attemptSecond = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return createKitchen(second, desired, null).getSlug();
        };

        List<Future<String>> futures = List.of(
                executor.submit(attemptFirst),
                executor.submit(attemptSecond));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<String> slugs = new ArrayList<>();
        for (Future<String> future : futures) {
            try {
                slugs.add(future.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException lostRace) {
                Throwable cause = lostRace.getCause();
                String msg = String.valueOf(cause.getMessage()).toLowerCase();
                assertThat(cause instanceof DataIntegrityViolationException
                                || msg.contains("unique") || msg.contains("duplicate"))
                        .as("a lost race surfaces as the DB uniqueness guard, not a silent overwrite; got: " + cause)
                        .isTrue();
            }
        }
        executor.shutdown();

        assertThat(slugs).as("at least one registration must succeed").isNotEmpty();
        assertThat(slugs).doesNotHaveDuplicates();
        List<String> persisted = kitchenRepository.findAll().stream()
                .map(Kitchen::getSlug)
                .filter(s -> s != null && s.startsWith("race-" + tag))
                .toList();
        assertThat(persisted).containsExactlyInAnyOrderElementsOf(slugs);
    }

    // ---------------- resolution & permanent aliases ----------------

    @Test
    @DisplayName("an Admin slug change keeps the old URL alive as a redirecting alias and is audited")
    void adminSlugChangeKeepsOldUrlAsAliasAndAudits() {
        String oldSlug = slug;
        String displayBefore = kitchen.getDisplayName();
        User actingAdmin = admin();

        Map<String, Object> result =
                adminService.changeKitchenSlug(kitchen.getId(), "Fresh URL " + tag, actingAdmin);

        // New canonical slug; display name untouched - URL change is not a rename.
        assertThat(result.get("slug")).isEqualTo("fresh-url-" + tag);
        assertThat(String.valueOf(result.get("publicUrl"))).endsWith("/kitchen/fresh-url-" + tag);
        Kitchen reloaded = reload();
        assertThat(reloaded.getSlug()).isEqualTo("fresh-url-" + tag);
        assertThat(reloaded.getDisplayName()).isEqualTo(displayBefore);

        // Old URL resolves to the SAME kitchen - direct and via alias.
        assertThat(slugService.resolveBySlug(oldSlug)).isPresent();
        assertThat(slugService.resolveBySlug(oldSlug).get().getId()).isEqualTo(kitchen.getId());
        assertThat(slugService.resolveBySlug("fresh-url-" + tag)).isPresent();
        assertThat(aliasRepository.existsByOldSlug(oldSlug))
                .as("the retired slug is kept as a permanent redirect row")
                .isTrue();

        // Public route: old slug 301s to the canonical path; current slug does not.
        assertThat(slugService.canonicalSlugForPublic(oldSlug, null))
                .isEqualTo("/kitchen/fresh-url-" + tag);
        assertThat(slugService.canonicalSlugForPublic("fresh-url-" + tag, null)).isNull();
        assertThat(slugService.canonicalSlugForPublic("no-such-" + tag, null)).isNull();

        // Audited with before/after.
        List<AdminAuditLog> rows = auditRepo
                .findByActionOrderByCreatedAtDesc(AdminAuditService.KITCHEN_SLUG_CHANGED).stream()
                .filter(r -> kitchen.getId().equals(r.getTargetId()))
                .toList();
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).getOldState()).isEqualTo(oldSlug);
        assertThat(rows.get(0).getNewState()).isEqualTo("fresh-url-" + tag);
        assertThat(rows.get(0).getActorId()).isEqualTo(actingAdmin.getId());
    }

    @Test
    @DisplayName("an Admin cannot take a slug held by another kitchen, by another kitchen's alias, or a blank one")
    void adminSlugChangeRejectsUnusableTargets() {
        User other = approvedSeller("Hold-" + tag);
        Kitchen holder = createKitchen(other, "holder-" + tag, null);
        User actingAdmin = admin();

        // Taken by another kitchen.
        assertThatThrownBy(() -> adminService.changeKitchenSlug(
                kitchen.getId(), "holder-" + tag, actingAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("That kitchen URL is already in use.");

        // Taken by ANOTHER kitchen's retired alias - adopting it would re-point
        // that kitchen's already-shared links here (shadowing its redirect).
        adminService.changeKitchenSlug(holder.getId(), "holder-new-" + tag, actingAdmin);
        assertThat(slugService.isTaken("holder-" + tag)).as("retired as an alias").isTrue();
        assertThatThrownBy(() -> adminService.changeKitchenSlug(
                kitchen.getId(), "holder-" + tag, actingAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("That kitchen URL is already in use.");

        // Blank / symbol-only is not a URL.
        assertThatThrownBy(() -> adminService.changeKitchenSlug(
                kitchen.getId(), "!!!", actingAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Choose a kitchen URL containing letters or numbers.");
    }

    @Test
    @DisplayName("an Admin can take back a slug this same kitchen retired (the revert case)")
    void adminSlugChangeAllowsRevertingToOwnAlias() {
        User actingAdmin = admin();
        adminService.changeKitchenSlug(kitchen.getId(), "first-move-" + tag, actingAdmin);

        Map<String, Object> reverted =
                adminService.changeKitchenSlug(kitchen.getId(), slug, actingAdmin);

        assertThat(reverted.get("slug")).isEqualTo(slug);
        assertThat(reload().getSlug()).isEqualTo(slug);
        // The interim slug now redirects back instead of breaking.
        assertThat(slugService.canonicalSlugForPublic("first-move-" + tag, null))
                .isEqualTo("/kitchen/" + slug);
    }

    // ---------------- status-based visibility of /kitchen/{slug} ----------------

    @Test
    @DisplayName("an unknown slug is a plain concealed 404, never the society-restriction message")
    void unknownSlugIsConcealedNotFound() {
        assertThatThrownBy(() -> kitchenService.getKitchenDetailBySlug("no-such-" + tag, null))
                .isInstanceOf(KitchenNotFoundException.class)
                .isNotInstanceOf(KitchenNotEligibleException.class);
    }

    @Test
    @DisplayName("a PENDING seller's storefront is not reachable by direct URL")
    void pendingSellerIsNotReachableByDirectUrl() {
        User pending = new User("Pending Seller " + tag, nextMobile(), "S-1", UserRole.SELLER);
        pending.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        pending = userRepository.save(pending);
        Kitchen pendingKitchen = new Kitchen("pending-kitchen-" + tag,
                "Pending Kitchen " + tag, "", null, pending);
        pendingKitchen.setSlug("pending-kitchen-" + tag);
        kitchenRepository.saveAndFlush(pendingKitchen);

        assertThatThrownBy(() -> kitchenService.getKitchenDetailBySlug(
                pendingKitchen.getSlug(), null))
                .as("404 concealment - pending is indistinguishable from missing")
                .isInstanceOf(KitchenNotFoundException.class);
    }

    @Test
    @DisplayName("a soft-removed storefront is not reachable by direct URL")
    void removedStorefrontIsNotReachableByDirectUrl() {
        seller.setStorefrontRemoved(true);
        userRepository.save(seller);

        assertThatThrownBy(() -> kitchenService.getKitchenDetailBySlug(slug, null))
                .isInstanceOf(KitchenNotFoundException.class)
                .isNotInstanceOf(KitchenNotEligibleException.class);
    }

    @Test
    @DisplayName("an Admin-suspended storefront is hidden from browsing as a plain 404")
    void adminSuspendedIsHiddenFromBrowsing() {
        kitchen.setStorefrontPaused(true);
        kitchenRepository.save(kitchen);

        assertThatThrownBy(() -> kitchenService.getKitchenDetailBySlug(slug, null))
                .isInstanceOf(KitchenNotFoundException.class)
                .isNotInstanceOf(KitchenNotEligibleException.class);

        // Account-level suspension hides it the same way.
        kitchen.setStorefrontPaused(false);
        kitchenRepository.save(kitchen);
        seller.setSellerApprovalStatus(SellerApprovalStatus.SUSPENDED);
        userRepository.save(seller);
        assertThatThrownBy(() -> kitchenService.getKitchenDetailBySlug(slug, null))
                .isInstanceOf(KitchenNotFoundException.class)
                .isNotInstanceOf(KitchenNotEligibleException.class);
    }

    @Test
    @DisplayName("a seller-paused (available today off) storefront shows the closed page, not a 404")
    void sellerPausedShowsClosedStorefrontNot404() {
        kitchen.setAvailableToday(false);
        kitchenRepository.save(kitchen);

        KitchenDetailDto detail = kitchenService.getKitchenDetailBySlug(slug, null);

        assertThat(detail.getProducts()).isEmpty();
        assertThat(detail.getPreorderProducts()).isEmpty();
        assertThat(detail.getKitchen().getPaused()).isTrue();
    }

    @Test
    @DisplayName("a buyer outside the kitchen's coverage is blocked on the direct URL with the exact message")
    void directUrlIsBlockedForBuyerOutsideCoverage() {
        User outsider = buyerIn(beta);

        assertThatThrownBy(() -> kitchenService.getKitchenDetailBySlug(slug, outsider))
                .isInstanceOf(KitchenNotEligibleException.class)
                .hasMessage(KitchenNotEligibleException.MESSAGE)
                .hasMessage("This kitchen doesn't currently serve your society.");
    }

    @Test
    @DisplayName("an APPROVED kitchen serves its detail to an in-coverage buyer and to a logged-out visitor")
    void approvedKitchenServesInCoverageBuyer() {
        KitchenDetailDto forBuyer = kitchenService.getKitchenDetailBySlug(slug, buyerIn(alpha));
        assertThat(forBuyer.getKitchen().getName()).isEqualTo(slug);

        KitchenDetailDto anonymous = kitchenService.getKitchenDetailBySlug(slug, null);
        assertThat(anonymous.getKitchen().getDisplayName()).isEqualTo(kitchen.getDisplayName());
    }

    // ---------------- seller immutability & pause rules ----------------

    @Test
    @DisplayName("the seller can never change the slug; renaming the display keeps it intact")
    void sellerCannotChangeSlugAndRenameKeepsSlug() {
        KitchenUpdateDto hijack = new KitchenUpdateDto();
        hijack.setName("hijacked-" + tag);
        assertThatThrownBy(() -> sellerService.updateKitchen(kitchen.getId(), hijack, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kitchen URL name is a protected field and cannot be changed");

        KitchenUpdateDto rename = new KitchenUpdateDto();
        rename.setDisplayName("Renamed Kitchen " + tag);
        sellerService.updateKitchen(kitchen.getId(), rename, seller);

        Kitchen reloaded = reload();
        assertThat(reloaded.getSlug()).isEqualTo(slug);
        assertThat(reloaded.getDisplayName()).isEqualTo("Renamed Kitchen " + tag);
        // The old URL still resolves after the rename.
        assertThat(slugService.resolveBySlug(slug)).isPresent();
    }

    @Test
    @DisplayName("the seller cannot pause or resume an Admin-suspended storefront")
    void sellerCannotPauseOrResumeAdminSuspended() {
        kitchen.setStorefrontPaused(true);
        kitchenRepository.save(kitchen);

        assertThatThrownBy(() -> sellerService.resumeKitchen(kitchen.getId(), seller))
                .isInstanceOf(SellerNotAuthorizedException.class);
        assertThatThrownBy(() -> sellerService.pauseKitchen(kitchen.getId(), seller))
                .isInstanceOf(SellerNotAuthorizedException.class);
    }

    // ---------------- marketplace links (Admin copy controls) ----------------

    @Test
    @DisplayName("the marketplace links the Admin copies always point at the canonical /kitchens route")
    void marketplaceLinksExposeCanonicalKitchensPath() {
        Map<String, Object> links = adminService.marketplaceLinks();

        assertThat(links.get("marketplacePath")).isEqualTo("/kitchens");
        assertThat(String.valueOf(links.get("marketplaceUrl"))).endsWith("/kitchens");
        assertThat(links).containsKey("baseUrl");
    }

    @Test
    @DisplayName("offerings from past dates are excluded from available today and preorder on storefront")
    void pastOfferingsAreExcludedFromAvailableTodayOnStorefront() {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);

        Product pastItem = new Product();
        pastItem.setName("Yesterday Special " + tag);
        pastItem.setKitchen(kitchen);
        pastItem.setPrice(new BigDecimal("120.00"));
        pastItem.setAvailableToday(true);
        pastItem.setAvailableDate(today.minusDays(1));
        pastItem.setReadyByTime(today.minusDays(1).toString() + "T10:00");
        productRepository.save(pastItem);

        Product todayItem = new Product();
        todayItem.setName("Today Special " + tag);
        todayItem.setKitchen(kitchen);
        todayItem.setPrice(new BigDecimal("150.00"));
        todayItem.setAvailableToday(true);
        todayItem.setAvailableDate(today);
        todayItem.setReadyByTime("12:00");
        productRepository.save(todayItem);

        Product futurePreorder = new Product();
        futurePreorder.setName("Tomorrow Feast " + tag);
        futurePreorder.setKitchen(kitchen);
        futurePreorder.setPrice(new BigDecimal("200.00"));
        futurePreorder.setIsPreorder(true);
        futurePreorder.setAvailableDate(today.plusDays(1));
        productRepository.save(futurePreorder);

        Product pastPreorder = new Product();
        pastPreorder.setName("Past Preorder " + tag);
        pastPreorder.setKitchen(kitchen);
        pastPreorder.setPrice(new BigDecimal("250.00"));
        pastPreorder.setIsPreorder(true);
        pastPreorder.setAvailableDate(today.minusDays(1));
        productRepository.save(pastPreorder);

        KitchenDetailDto detail = kitchenService.getKitchenDetailBySlug(slug, null);

        // Today must contain only today's item, NOT yesterday's item
        assertThat(detail.getProducts()).extracting(ProductDto::getName)
                .contains("Today Special " + tag)
                .doesNotContain("Yesterday Special " + tag);

        // Preorder must contain only future preorder, NOT past preorder
        assertThat(detail.getPreorderProducts()).extracting(ProductDto::getName)
                .contains("Tomorrow Feast " + tag)
                .doesNotContain("Past Preorder " + tag);
    }
}
