package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.GlobalExceptionHandler;
import com.example.my_first_spring_api.dto.ApiErrorDto;
import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.exception.KitchenNotEligibleException;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.QuickPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The kitchen-ineligibility PRESENTATION requirement.
 *
 * <p>Behaviour that already worked and must keep working: a buyer whose Society
 * is outside a kitchen's service area cannot open, order from, enquire about or
 * favourite it, and the refusal is concealed behind an HTTP 404 so the kitchen's
 * existence is not leaked. That concealment is deliberate and is pinned by
 * {@code Requirements1920IntegrationTest}.
 *
 * <p>The gap these tests close: the buyer was told "Kitchen not found with id:
 * N" - indistinguishable from a typo - and was given no way back to the
 * marketplace. The acceptance criteria require the exact wording
 * "This kitchen doesn't currently serve your society." plus an "Explore
 * kitchens" action.</p>
 *
 * <p>The fix keeps the 404 and the {@code KitchenNotFoundException} type by
 * throwing a subclass, so every existing {@code instanceof} assertion and all
 * concealment behaviour are untouched. Only the body gains a machine-readable
 * {@code KITCHEN_NOT_ELIGIBLE} code, which is what lets the UI distinguish
 * "not served" from "does not exist" without message-sniffing.</p>
 */
@ExtendWith(MockitoExtension.class)
class KitchenIneligibilityMessageTest {

    /** The exact required wording, ASCII apostrophe included. */
    private static final String REQUIRED_MESSAGE = "This kitchen doesn't currently serve your society.";

    @Mock private KitchenRepository kitchenRepository;
    @Mock private ProductRepository productRepository;
    @Mock private QuickPostRepository quickPostRepository;
    @Mock private AnalyticsService analyticsService;

    private KitchenService kitchenService;

    private static final Long KITCHEN_ID = 1L;

    @BeforeEach
    void setUp() {
        kitchenService = new KitchenService(kitchenRepository, productRepository,
                quickPostRepository, analyticsService);
        lenient().when(productRepository.findByKitchen(any())).thenReturn(List.of());
        lenient().when(quickPostRepository.findByKitchenAndPostedDateOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
    }

    private Kitchen kitchenServing(String serviceAreas) {
        User seller = new User("Inel Kitchen Owner", "9000000011", "K-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        Kitchen k = new Kitchen("inel-kitchen", "Inel Kitchen", "desc", null, seller);
        k.setSociety("Beta Society");
        k.setServiceAreas(serviceAreas);
        k.setAvailableToday(true);
        return k;
    }

    private User buyerIn(String society) {
        User b = new User("Buyer " + society, "9000000012", "F-1", UserRole.BUYER);
        b.setSociety(society);
        return b;
    }
// ---------------- 1 + 2: ineligible -> dedicated type AND exact message ----------------

    @Test
    void ineligibleBuyerGetsTheDedicatedExceptionWithTheExactMessage() {
        when(kitchenRepository.findById(KITCHEN_ID)).thenReturn(Optional.of(kitchenServing("Beta Society")));

        assertThatThrownBy(() -> kitchenService.getKitchenDetailById(KITCHEN_ID, buyerIn("Alpha Society")))
                .isInstanceOf(KitchenNotEligibleException.class)
                .hasMessage(REQUIRED_MESSAGE);

        // Requirement 2: the dedicated type must stay compatible with the existing
        // KitchenNotFoundException contract (Requirements1920IntegrationTest asserts it).
        assertThatThrownBy(() -> kitchenService.getKitchenDetailById(KITCHEN_ID, buyerIn("Alpha Society")))
                .isInstanceOf(KitchenNotFoundException.class);
        assertThat(new KitchenNotEligibleException(KITCHEN_ID)).isInstanceOf(KitchenNotFoundException.class);
        assertThat(KitchenNotEligibleException.MESSAGE).isEqualTo(REQUIRED_MESSAGE);
    }

    // ---------------- 3: a genuinely nonexistent kitchen keeps its old message ----------------

    @Test
    void genuinelyMissingKitchenKeepsTheOriginalNotFoundMessage() {
        when(kitchenRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kitchenService.getKitchenDetailById(999L, buyerIn("Alpha Society")))
                .isInstanceOf(KitchenNotFoundException.class)
                .isNotInstanceOf(KitchenNotEligibleException.class)
                .hasMessage("Kitchen not found with id: 999");
    }

    // ---------------- 4: an eligible buyer is unaffected ----------------

    @Test
    void eligibleBuyerStillGetsTheKitchen() {
        when(kitchenRepository.findById(KITCHEN_ID)).thenReturn(Optional.of(kitchenServing("Alpha Society")));

        assertThatCode(() -> kitchenService.getKitchenDetailById(KITCHEN_ID, buyerIn("Alpha Society")))
                .doesNotThrowAnyException();
        assertThat(kitchenService.getKitchenDetailById(KITCHEN_ID, buyerIn("Alpha Society"))).isNotNull();
    }

    // ---------------- 5: anonymous browsing is unchanged ----------------

    @Test
    void anonymousVisitorStillBrowsesTheKitchen() {
        when(kitchenRepository.findById(KITCHEN_ID)).thenReturn(Optional.of(kitchenServing("Beta Society")));

        assertThat(kitchenService.getKitchenDetailById(KITCHEN_ID, null))
                .as("a logged-out visitor has no Society yet, so concealment must not apply")
                .isNotNull();
    }
// ---------------- HTTP contract: still 404, but distinguishable ----------------

    @Test
    void ineligibleKitchenStillAnswers404WithItsOwnCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiErrorDto> response =
                handler.handleKitchenNotEligible(new KitchenNotEligibleException(KITCHEN_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo(KitchenNotEligibleException.CODE);
        assertThat(response.getBody().getMessage()).isEqualTo(REQUIRED_MESSAGE);
        assertThat(response.getBody().getStatus()).isEqualTo(404);

        // A missing kitchen keeps the plain NOT_FOUND code, so the UI cannot
        // mistake it for a confirmed service-area restriction.
        ResponseEntity<ApiErrorDto> missing = handler.handleNotFound(new KitchenNotFoundException(999L));
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().getError()).isEqualTo("NOT_FOUND");
        assertThat(missing.getBody().getMessage()).isEqualTo("Kitchen not found with id: 999");
        assertThat(missing.getBody().getError()).isNotEqualTo(KitchenNotEligibleException.CODE);
    }

    // ---------------- 8: both frontend error paths carry the message + action ----------------

    @Test
    void bothFrontendErrorPathsCarryTheMessageAndExploreAction() throws IOException {
        String buyerJs = readResource("/js/buyer.js");

        assertThat(buyerJs).contains(REQUIRED_MESSAGE);

        // The action must be the real existing marketplace route, not a new screen.
        assertThat(buyerJs)
                .as("an Explore kitchens action linking to the existing #/kitchens route")
                .contains("href=\"#/kitchens\">Explore kitchens</a>");

        // Heading required by the criteria.
        assertThat(buyerJs).contains("'Kitchen not available'");

        // Both the kitchen page and the homemade-store page must route through the
        // one shared helper (1 definition + exactly 2 call sites).
        assertThat(buyerJs.split("ineligibleKitchenHtml\\(", -1)).hasSize(4);

        // It must branch on the machine-readable code, never on message text.
        assertThat(buyerJs).contains("e.data.error");
        assertThat(buyerJs).contains("KITCHEN_NOT_ELIGIBLE");
        assertThat(buyerJs)
                .as("a missing kitchen keeps the plain not-found wording")
                .contains("'Kitchen not available', e.message);");
    }

    /**
     * Same loading strategy as the existing script-structure tests: the asset is
     * on the test classpath once packaged, and straight off disk otherwise.
     */
    private static String readResource(String path) throws IOException {
        try (InputStream in = KitchenIneligibilityMessageTest.class.getResourceAsStream(path)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return Files.readString(Path.of("src", "main", "resources", "static", path.substring(1)),
                StandardCharsets.UTF_8);
    }
}