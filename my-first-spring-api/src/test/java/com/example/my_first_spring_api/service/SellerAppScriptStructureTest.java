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
}
