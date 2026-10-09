package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 regression - the Seller app never finished loading.
 *
 * Commit 70011c8 extracted a shared {@code offeringFormHtml} helper but inserted
 * {@code sellerCreateView} in the middle of it, before the helper was closed.
 * That nested the view function (and every function after it) inside the helper,
 * so the top-level {@code sellerRoutes} map threw
 * "ReferenceError: sellerCreateView is not defined" while the script was still
 * being evaluated.
 *
 * A top-level throw aborts the whole seller.js bundle, so nothing was registered:
 * no demo-login, no routing, no render. The page therefore stayed on the static
 * spinner from seller.html forever, with zero API traffic.
 *
 * These checks guard the exact failure mode: a route handler must be a real
 * top-level function declaration, and the shared offering form must still render
 * every field (so a "just add the missing brace" fix cannot silently drop
 * Orders Open/Close, Delivery, Quantity, categories or the submit button).
 */
class SellerAppScriptStructureTest {

    private static String sellerJs;

    @BeforeAll
    static void loadScript() throws IOException {
        sellerJs = readResource("/js/seller.js");
    }

    /** Reads from the classpath (works in tests and in the packaged jar). */
    private static String readResource(String path) throws IOException {
        try (InputStream in = SellerAppScriptStructureTest.class.getResourceAsStream(path)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path onDisk = Path.of("src", "main", "resources", "static", path.substring(1));
        return Files.readString(onDisk, StandardCharsets.UTF_8);
    }

    private static List<String> routeHandlers() {
        Matcher map = Pattern.compile("var\\s+sellerRoutes\\s*=\\s*\\{([\\s\\S]*?)\\n\\};")
                .matcher(sellerJs);
        assertThat(map.find()).as("sellerRoutes map must exist in seller.js").isTrue();
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile(":\\s*(seller\\w+View)").matcher(map.group(1));
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /**
     * Collects the names of functions declared at brace depth 0, i.e. genuine
     * top-level declarations that JavaScript hoists into global scope.
     *
     * Comments and string/template literals are skipped first, otherwise braces
     * and the word "function" inside markup strings would corrupt the depth.
     * This is what distinguishes a real global declaration from one that merely
     * happens to start at column 0 while nested inside another function.
     */
    private static Set<String> topLevelFunctions(String js) {
        Set<String> found = new LinkedHashSet<>();
        int depth = 0;
        boolean expectName = false;
        StringBuilder token = new StringBuilder();
        int i = 0;
        final int n = js.length();
        while (i < n) {
            char c = js.charAt(i);
            char next = (i + 1 < n) ? js.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {                    // line comment
                while (i < n && js.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && next == '*') {                    // block comment
                i += 2;
                while (i + 1 < n && !(js.charAt(i) == '*' && js.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {          // string / template
                char quote = c;
                i++;
                while (i < n) {
                    char d = js.charAt(i);
                    if (d == '\\') { i += 2; continue; }
                    if (d == quote) { i++; break; }
                    i++;
                }
                token.setLength(0);
                continue;
            }
            if (c == '{') { depth++; i++; token.setLength(0); continue; }
            if (c == '}') { depth--; i++; token.setLength(0); expectName = false; continue; }

            if (Character.isJavaIdentifierStart(c) || c == '$') {
                token.setLength(0);
                while (i < n && (Character.isJavaIdentifierPart(js.charAt(i)) || js.charAt(i) == '$')) {
                    token.append(js.charAt(i));
                    i++;
                }
                String word = token.toString();
                if ("function".equals(word)) {
                    expectName = (depth == 0);
                } else if (expectName) {
                    found.add(word);
                    expectName = false;
                }
                continue;
            }
            i++;
        }
        return found;
    }

    @Test
    void everySellerRouteHandlerIsATopLevelFunction() {
        List<String> handlers = routeHandlers();
        assertThat(handlers).isNotEmpty();

        Set<String> globals = topLevelFunctions(sellerJs);
        assertThat(globals)
                .as("scanner must find the known top-level seller entry points")
                .contains("sellerRender", "sellerHomeView", "sellerCreateView");

        for (String fn : handlers) {
            assertThat(globals)
                    .as("route handler %s must be declared at top level in seller.js; a declaration "
                        + "nested inside another function parses fine but is NOT hoisted, so the "
                        + "sellerRoutes map throws ReferenceError and the whole app stays on its "
                        + "spinner", fn)
                    .contains(fn);
        }
    }

    @Test
    void sharedOfferingFormStillRendersEveryField() {
        int start = sellerJs.indexOf("function offeringFormHtml");
        int end = sellerJs.indexOf("// SCREEN 3: CREATE OFFERING");
        assertThat(start).as("offeringFormHtml helper must exist").isGreaterThanOrEqualTo(0);
        assertThat(end).as("sellerCreateView must follow offeringFormHtml").isGreaterThan(start);

        String form = sellerJs.substring(start, end);
        // The form tail lives inside the helper; a truncated helper would
        // silently remove these from BOTH create and edit offering.
        assertThat(form).contains("orderWindowStart");            // Orders Open
        assertThat(form).contains("orderWindowEnd");              // Orders Close
        assertThat(form).contains("readyByTime");                 // Delivery / Ready By
        assertThat(form).contains("maxQuantity");                 // Quantity / unlimited
        assertThat(form).contains("offeringCategoryBox");        // Categories
        assertThat(form).contains("toggle-favourite");            // Mark as Favourite
        assertThat(form).contains("type=\"submit\"");             // Publish / Save button
        assertThat(form).contains("</form>");                     // form is closed
        assertThat(form).contains("return h;");
    }

    @Test
    void sellerScriptBracesAreBalanced() {
        long open = sellerJs.chars().filter(c -> c == '{').count();
        long close = sellerJs.chars().filter(c -> c == '}').count();
        assertThat(open).as("unbalanced braces in seller.js").isEqualTo(close);
    }

    @Test
    void sellerBootRegistersRenderSoTheSpinnerCanBeReplaced() {
        // Boot must authenticate, then render; otherwise the static spinner in
        // seller.html is never replaced.
        assertThat(sellerJs).contains("/api/seller-app/demo-login");
        assertThat(sellerJs).contains("await sellerRender()");
        assertThat(sellerJs).contains("location.hash = '#/home'");
    }

    @Test
    void sellerRenderRevalidatesTheServerSessionBeforeOwnerScopedCalls() {
        // The Buyer and Seller apps share one browser session, so a Buyer login
        // elsewhere silently replaces the seller identity. sellerRender must ask
        // the server who the session is instead of trusting cached state.
        assertThat(sellerJs)
                .as("seller.js must define the session guard")
                .contains("async function ensureSellerSession()");
        assertThat(sellerJs)
                .as("the guard must consult the server")
                .contains("api('/api/auth/me')");
        assertThat(sellerJs)
                .as("the guard must be able to restore the seller session")
                .contains("api('/api/seller-app/demo-login'");

        // The guard must run before the route function is invoked, otherwise the
        // dashboard still renders the raw 403.
        int guard = sellerJs.indexOf("await ensureSellerSession()");
        int routeCall = sellerJs.indexOf("await route.fn(route.arg)");
        assertThat(guard).isGreaterThanOrEqualTo(0);
        assertThat(routeCall).isGreaterThan(guard);
    }

    @Test
    void sellerBootDoesNotSilentlySwallowAuthenticationFailure() {
        // A failed seller login must surface a retry state, not fall through to
        // render an "Only sellers can perform this action" screen.
        assertThat(sellerJs).doesNotContain("console.warn('demo-login failed:'");
        assertThat(sellerJs).contains("sellerAuthErrorHtml()");
        assertThat(sellerJs).contains("data-action=\"seller-retry\"");
    }

    /**
     * P0 regression - the pre-route guard alone does not stop the reported error.
     *
     * ensureSellerSession() and the route's own request are two separate HTTP
     * calls, so a Buyer login landing between them still made an otherwise valid
     * seller request fail 403, and the view rendered that raw error permanently
     * ("Could not load dashboard / Only sellers can perform this action") with no
     * way back except a manual reload.
     *
     * seller-scoped reads therefore go through sellerApi(), which heals the
     * session and retries once. These assertions pin that contract so the fix
     * cannot be silently dropped, and cannot become an unbounded retry loop.
     */
    @Test
    void sellerScopedRequestsRetryOnceAfterAnAuthFailure() {
        assertThat(sellerJs)
                .as("seller.js must define the self-healing seller request helper")
                .contains("async function sellerApi(path, opts)");
        assertThat(sellerJs)
                .as("only 401/403 may trigger a re-login and retry")
                .contains("function isSellerAuthError(err)");
        assertThat(sellerJs).contains("err.status === 401 || err.status === 403");
        assertThat(sellerJs)
                .as("the retry must re-establish the session, not invent one")
                .contains("if (!(await restoreSellerSession())) throw err;");

        // Bounded: exactly one retry after the catch, and no loop construct.
        String helper = sellerJs.substring(
                sellerJs.indexOf("async function sellerApi(path, opts)"),
                sellerJs.indexOf("function sellerAuthErrorHtml()"));
        assertThat(helper.split("return await api\\(path, opts\\);", -1))
                .as("sellerApi must call the transport at most twice (initial + one retry)")
                .hasSize(3);
        assertThat(helper).doesNotContain("while");
        assertThat(helper).doesNotContain("for (");
        assertThat(helper).doesNotContain("setTimeout");
        assertThat(helper).doesNotContain("setInterval");
    }

    @Test
    void everySellerScopedCallGoesThroughTheSelfHealingHelper() {
        // restoreSellerSession() performs the login itself, so it must keep using
        // the raw api(); every OTHER /api/seller-app|/api/seller call must heal.
        String body = sellerJs.substring(sellerJs.indexOf("function sellerAuthErrorHtml()"));
        Matcher raw = Pattern.compile("(?<!seller)\\bapi\\('(/api/(?:seller-app|seller)/[^']*)'").matcher(body);
        while (raw.find()) {
            assertThat(raw.group(1))
                    .as("seller-scoped call must use sellerApi() so a lost session self-heals")
                    .doesNotStartWith("/api/seller-app/demo-login");
        }
        // And the heal path must not recurse into itself.
        String restore = sellerJs.substring(
                sellerJs.indexOf("async function restoreSellerSession()"),
                sellerJs.indexOf("function isSellerAuthError(err)"));
        assertThat(restore)
                .as("restoreSellerSession must call the transport directly, never sellerApi")
                .doesNotContain("sellerApi(");
    }

    @Test
    void theSelfHealDoesNotWeakenAuthorizationOrFakeData() {
        // The fix must never fabricate a seller identity or dashboard payload:
        // the expected role is still read back from the server's own response.
        assertThat(sellerJs)
                .as("role must be verified from the server response")
                .contains("s.role === 'SELLER'");
        assertThat(sellerJs)
                .as("no hardcoded seller id / kitchen may be introduced")
                .doesNotContain("kitchenId: 1,")
                .doesNotContain("userId === 3");
        // Genuine non-auth failures must still surface, not be swallowed.
        assertThat(sellerJs)
                .as("a non-auth error must propagate untouched")
                .contains("if (!isSellerAuthError(err)) throw err;");
    }

    // ------------------------------------------------------------------
    // P0 regression - "View Orders" for an offering rendered no customer
    // rows at all.
    //
    // sellerOrderDetailView() returns an HTML STRING, and sellerRender() only
    // assigns that string to view.innerHTML AFTER the function resolves. The
    // view was calling document.getElementById('offeringName') and
    // renderOfferingCustomers() on elements that did not exist yet, so the
    // header stayed blank and the customer list was permanently empty even
    // though the API had returned the rows.
    // ------------------------------------------------------------------

    @Test
    void theOfferingOrdersViewBuildsItsRowsAsAStringNotThroughTheDom() {
        int start = sellerJs.indexOf("async function sellerOrderDetailView(");
        int end = sellerJs.indexOf("async function sellerOrderDetailByOrderView(");
        assertThat(start).as("sellerOrderDetailView must exist").isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String view = sellerJs.substring(start, end);

        // A view returning a string must not reach into the live document.
        assertThat(view)
                .as("the view must not query the DOM before sellerRender inserts it")
                .doesNotContain("document.getElementById");
        assertThat(view)
                .as("customer rows must be inlined into the returned string")
                .contains("offeringCustomersHtml(detail)");
        assertThat(sellerJs)
                .as("the DOM-writing variant must be gone")
                .doesNotContain("function renderOfferingCustomers(");
    }

    @Test
    void theOfferingOrdersViewKeepsFiltersAndDropsTheSortByItemControl() {
        int start = sellerJs.indexOf("async function sellerOrderDetailView(");
        int end = sellerJs.indexOf("async function sellerOrderDetailByOrderView(");
        String view = sellerJs.substring(start, end);

        // Society + status filters, both wired to the existing change handlers.
        assertThat(view).contains("data-action=\"set-offering-society\"");
        assertThat(view).contains("data-action=\"set-offering-status\"");
        assertThat(view).contains("All Societies");
        assertThat(view).contains("All Status");
        // A per-item sort control must not be offered: the seller is already
        // looking at a single offering. Asserted on the rendered option label
        // and on the absence of any sort action.
        assertThat(view)
                .doesNotContain(">Sort by Item<")
                .doesNotContain("Sort by Item</option>")
                .doesNotContain("set-offering-sort");
        // Summary counts come from the backend payload, never hardcoded.
        assertThat(view).contains("detail.totalOrders").contains("detail.totalPlates")
                .contains("detail.totalRevenue");
    }

    @Test
    void offeringCardActionsKeepTheirHandlersAndGainACompactHierarchy() {
        // The dashboard was redesigned to the approved Stitch reference. The
        // offering card markup now lives in the reusable sdOfferingCardHtml()
        // component that sellerHomeView() calls, so this slice starts at that
        // component instead of at sellerHomeView itself. Every BEHAVIOURAL
        // guarantee below is unchanged - only the CSS class names moved from the
        // old oc-act-* vocabulary to the new sd-btn--* design tokens.
        int start = sellerJs.indexOf("function sdOfferingCardHtml(");
        int end = sellerJs.indexOf("async function sellerHistoryView(");
        assertThat(start).as("sdOfferingCardHtml must exist").isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String home = sellerJs.substring(start, end);

        // Every action keeps its ORIGINAL data-action, so behaviour is unchanged.
        assertThat(home).contains("data-action=\"mark-soldout\"");
        assertThat(home).contains("data-action=\"pause-orders\"");
        assertThat(home).contains("data-action=\"resume-orders\"");
        assertThat(home).contains("data-action=\"edit-offering\"");
        // V3 §11 supersedes the earlier "stepper is preserved" pin: the inline
        // quantity +/- stepper is removed from dashboard cards (inverted pin,
        // so it can never quietly return).
        assertThat(home)
                .as("no inline stock stepper on the dashboard card")
                .doesNotContain("data-action=\"inv-inc\"")
                .doesNotContain("data-action=\"inv-dec\"");
        // View Orders still routes by the offering id.
        assertThat(home).contains("href=\"#/order-detail/' + p.id + '\"");
        // Compact hierarchy classes replace the stacked full-width buttons.
        assertThat(home).contains("sd-card__actions").contains("sd-btn--primary")
                .contains("sd-btn--edit").contains("sd-btn--pause")
                .contains("sd-btn--resume").contains("sd-btn--soldout");
        // Pause is still only offered when the offering is actually pausable.
        assertThat(home).contains("if (!p.soldOut && !p.ordersPaused) h += '<button class=\"sd-btn sd-btn--pause\"");
        // The operational figures the seller relies on must stay on the card.
        assertThat(home)
                .as("booked and available quantities")
                .contains("booked").contains("available")
                .as("order deadline and delivery time")
                .contains("sellerOrdersCloseLabel(p)").contains("sellerDeliveryLabel(p)")
                .as("real price")
                .contains("money(p.price)")
                .as("status badge is still the shared one, so states stay distinct")
                .contains("offeringStatusBadge(p)");
    }

    /**
     * P0 regression - the offering-orders filters never applied.
     *
     * A &lt;select&gt; reports the chosen option through the 'change' event.
     * Clicking the dropdown only fires 'click', and at that moment the control
     * still holds the PREVIOUS value, so handling these actions in the click
     * listener left the society/status filters inert: the view re-rendered with
     * the old filter and the "Showing N of M" indicator never appeared.
     */
    @Test
    void theOfferingOrderFiltersAreHandledOnChangeNotOnlyOnClick() {
        int ch = sellerJs.indexOf("document.addEventListener('change'");
        assertThat(ch).as("a change listener must exist").isGreaterThanOrEqualTo(0);
        int end = sellerJs.indexOf("// Live Quick Post character feedback", ch);
        assertThat(end).as("the change listener end marker must exist").isGreaterThan(ch);
        String changeListener = sellerJs.substring(ch, end);

        assertThat(changeListener)
                .as("society filter must react to change")
                .contains("data-action=\"set-offering-society\"");
        assertThat(changeListener)
                .as("status filter must react to change")
                .contains("data-action=\"set-offering-status\"");
        // It must re-render so the filtered rows and the indicator update.
        assertThat(changeListener).contains("sellerRender()");
    }

    /**
     * P1 regression - Favourites and History showed no data.
     *
     * One real cause was in the render path: the favourites read collapsed a failed
     * request into an empty array, so a seller with saved templates saw the
     * "no favourites" state and had no way to tell the two apart. The History view
     * already kept the two states separate and must keep doing so.
     *
     * The "Create from Favourite" card also carried a data-action no handler ever
     * implemented, so clicking it did nothing at all.
     */
    @Test
    void aFailedFavouritesReadIsNeverShownAsAnEmptyFavouriteList() {
        int start = sellerJs.indexOf("async function sellerAddView(");
        int end = sellerJs.indexOf("// SCREEN 1: SELLER DASHBOARD");
        assertThat(start).as("sellerAddView must exist").isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String add = sellerJs.substring(start, end);

        assertThat(add).as("the loader must keep the failure separate from the list")
                .contains("S.favError = null")
                .contains("S.favError = e.message");
        assertThat(add).as("a failure must never be flattened into an empty favourite list")
                .doesNotContain("catch (e) { S.favTemplates = []; }");
        assertThat(add).as("the failure state offers a retry")
                .contains("data-action=\"retry-favourites\"");
        // Saved templates still render from persisted state through the same pills.
        assertThat(add).contains("/api/seller-app/templates")
                .contains("data-action=\"use-template\"").contains("fav-pill");
    }

    @Test
    void everyFavouritesAndHistoryActionOnScreenHasAHandler() {
        assertThat(sellerJs)
                .as("the Favourites pathway card used to carry an unhandled action")
                .contains("case 'go-use-favourite'")
                .contains("case 'retry-favourites'")
                .contains("case 'use-template'");

        int start = sellerJs.indexOf("async function sellerHistoryView(");
        int end = sellerJs.indexOf("// SCREEN 4: QUICK POST");
        assertThat(start).as("sellerHistoryView must exist").isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String history = sellerJs.substring(start, end);
        // Empty and error stay two different states, and records come from the API.
        assertThat(history).contains("/api/seller-app/history")
                .contains("No previous items")
                .contains("Could not load history");
    }
}
