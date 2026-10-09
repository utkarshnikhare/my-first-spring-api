package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seller Kitchen Preview regression guard.
 *
 * <p>The "Preview Kitchen Page" button was a stub that only raised a toast, so
 * tapping it never left the Seller app and the seller was left looking at
 * "Opening kitchen preview...". The preview is supposed to open the EXISTING
 * buyer-facing kitchen page.</p>
 *
 * <p>These assertions pin the wiring that makes that work, so the button cannot
 * silently degrade back into a toast-only stub, cannot lose the seller's own
 * kitchen id, and cannot leave a loading toast behind when it cannot open.</p>
 *
 * <p>Static-source assertions follow the existing convention in this repository
 * ({@code SellerAppScriptStructureTest}, {@code BuyerHomeOverflowLayoutTest}).</p>
 */
class SellerKitchenPreviewTest {

    private static String sellerJs;
    private static String buyerJs;

    @BeforeAll
    static void load() throws IOException {
        Path staticDir = Paths.get("src", "main", "resources", "static");
        sellerJs = read(staticDir.resolve("js").resolve("seller.js"));
        buyerJs = read(staticDir.resolve("js").resolve("buyer.js"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    void thePreviewButtonStillExistsOnTheSellerKitchenScreen() {
        assertThat(sellerJs)
                .contains("data-action=\"preview-kitchen\">Preview Kitchen Page</button>");
    }

    /**
     * The defect itself: the handler must not be a bare toast. It has to
     * navigate to the existing buyer kitchen route.
     */
    @Test
    void thePreviewHandlerNavigatesInsteadOfOnlyShowingAToast() {
        assertThat(sellerJs)
                .as("the stub that only toasted must be gone")
                .doesNotContain("case 'preview-kitchen': toast('Opening kitchen preview...', 'info'); break;");
        assertThat(sellerJs)
                .as("the preview must load the existing buyer kitchen page by id")
                .contains("location.href = '/index.html#/kitchen/' + encodeURIComponent(previewId);");
    }

    /**
     * The preview must show THIS seller's kitchen - resolved from the kitchen the
     * Seller Kitchen screen already loaded, never a hard-coded or default id.
     */
    @Test
    void thePreviewUsesTheCurrentSellersOwnKitchenId() {
        assertThat(sellerJs)
                .as("the id must come from the seller's own kitchen state")
                .contains("var previewKitchen = S.myKitchen || S.kitchen;")
                .contains("var previewId = previewKitchen && previewKitchen.id;");
        assertThat(sellerJs)
                .as("a missing id must re-read the seller's kitchen, not guess")
                .contains("previewKitchen = await sellerApi('/api/seller/kitchen');");
    }

    /** The loading toast must never be the last thing the seller sees. */
    @Test
    void aFailedPreviewClearsTheLoadingStateWithAnError() {
        assertThat(sellerJs)
                .as("the loading message is shown only once the kitchen id is known")
                .contains("if (!previewId) {")
                .contains("toast('Could not load your kitchen. Please retry.', 'error');")
                .contains("toast('No kitchen found to preview. Publish your kitchen first.', 'error');");
    }

    /**
     * The existing buyer route and its API must remain intact - the fix reuses
     * them instead of introducing a parallel preview system.
     */
    @Test
    void theExistingBuyerKitchenRouteAndApiAreUntouched() {
        assertThat(buyerJs)
                .as("buyer still resolves the kitchen page from the hash route")
                .contains("async function kitchenPageView(hash)")
                .contains("var id = hash.split('/')[2];")
                .contains("api('/api/kitchens/id/' + id)");
    }

    /** No second preview API or duplicate kitchen page may be introduced. */
    @Test
    void noSecondPreviewEndpointOrDuplicateKitchenPageWasAdded() {
        assertThat(sellerJs)
                .as("the preview must not call a bespoke preview API")
                .doesNotContain("/api/seller/preview")
                .doesNotContain("/api/seller-app/preview");
        assertThat(sellerJs)
                .as("no hard-coded environment kitchen id may be used")
                .doesNotContain("/index.html#/kitchen/1'");
    }
}
