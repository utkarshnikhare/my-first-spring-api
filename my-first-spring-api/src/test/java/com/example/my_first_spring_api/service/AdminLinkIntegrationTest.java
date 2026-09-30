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
 * Regression cover for the Admin entry-link integration.
 *
 * <p>Two wiring defects were found by driving the real console in a browser:
 * <ol>
 *   <li><b>Analytics tab was dead.</b> {@code adminRoutes} is built at the top of
 *       admin.js, but {@code var adminAnalyticsView = adminTrafficView;} sat far
 *       below it. A {@code var} is not initialised until execution reaches it, so
 *       the route entry captured {@code undefined} and the tab silently fell
 *       through to Home.</li>
 *   <li><b>Order detail never opened.</b> The click handler fetched the order and
 *       then passed the resulting OBJECT to {@code adminOrderDetailView(id)},
 *       which fetched {@code /api/admin/orders/} + id again - producing
 *       {@code /api/admin/orders/[object Object]} (HTTP 400) and leaving the
 *       order list on screen.</li>
 * </ol>
 */
class AdminLinkIntegrationTest {

    private static String adminJs;
    private static String adminHtml;
    private static String securityConfig;

    @BeforeAll
    static void load() throws IOException {
        Path staticDir = Paths.get("src", "main", "resources", "static");
        adminJs = read(staticDir.resolve(Paths.get("js", "admin.js")));
        adminHtml = read(staticDir.resolve("admin.html"));
        securityConfig = read(Paths.get("src", "main", "java", "com", "example",
                "my_first_spring_api", "SecurityConfig.java"));
    }

    private static String read(Path p) throws IOException {
        assertThat(p).as("must exist: %s", p).exists();
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    @Test
    void everyAdminRouteResolvesToARealViewFunction() {
        // The Analytics regression: the assignment must come BEFORE the route
        // table, or the entry captures `undefined` and the tab dead-ends on Home.
        int assignment = adminJs.indexOf("var adminAnalyticsView = adminTrafficView()");
        int routeTable = adminJs.indexOf("var adminRoutes = {");
        assertThat(assignment).as("adminAnalyticsView must be initialised").isGreaterThanOrEqualTo(0);
        assertThat(routeTable).as("the route table must exist").isGreaterThanOrEqualTo(0);
        assertThat(assignment)
                .as("adminAnalyticsView must be assigned BEFORE the route table is built; " +
                        "a `var` is undefined until execution reaches it")
                .isLessThan(routeTable);
        assertThat(adminJs)
                .as("the route must map to the inner view function, not the factory")
                .doesNotContain("adminAnalyticsView = adminTrafficView;");
    }

    @Test
    void theAnalyticsViewReturnsItsMarkupInsteadOfSelfAssigning() {
        int start = adminJs.indexOf("function adminTrafficView()");
        assertThat(start).as("adminTrafficView must exist").isGreaterThanOrEqualTo(0);
        String view = adminJs.substring(start, adminJs.indexOf("function renderTrafficContent("));
        // adminRender does `view.innerHTML = await route.fn(...) || ''`, so a view
        // that assigns innerHTML itself and returns undefined is wiped at once.
        assertThat(view)
                .as("must return its markup like every other admin view")
                .contains("return h;");
    }

    @Test
    void orderDetailReceivesTheIdNotTheFetchedRecord() {
        int start = adminJs.indexOf("case 'admin-order-detail'");
        assertThat(start).as("the admin-order-detail handler must exist").isGreaterThanOrEqualTo(0);
        String handler = adminJs.substring(start, adminJs.indexOf("case 'admin-back-orders'"));
        // The double fetch produced /api/admin/orders/[object Object] -> HTTP 400.
        assertThat(handler)
                .as("must pass the id straight through")
                .contains("adminOrderDetailView(oid)")
                .doesNotContain("adminOrderDetailView(detail)")
                .doesNotContain("adminOrderDetailView(o)");
    }

    @Test
    void orderDetailViewStillFetchesById() {
        int start = adminJs.indexOf("async function adminOrderDetailView(");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(adminJs.substring(start, start + 300))
                .as("the view is the single place that loads the order")
                .contains("api('/api/admin/orders/' + id)");
    }

    @Test
    void theAdminEntryRouteIsRegisteredInTheHtml() {
        assertThat(adminHtml)
                .as("the console page and its assets must be declared")
                .contains("/js/admin.js")
                .contains("/css/admin.css")
                .contains("id=\"adminNav\"")
                .contains("id=\"adminTopbar\"");
    }

    @Test
    void adminApisRemainRoleProtected() {
        // The entry link must not have weakened the backend. /api/admin/** is
        // ADMIN+SUPER_ADMIN; /api/superadmin/** stays SUPER_ADMIN only. The
        // matcher order matters, so the superadmin rule must be declared first.
        int superRule = securityConfig.indexOf(".requestMatchers(\"/api/superadmin/**\").hasRole(\"SUPER_ADMIN\")");
        int adminRule = securityConfig.indexOf(".requestMatchers(\"/api/admin/**\").hasAnyRole(\"ADMIN\", \"SUPER_ADMIN\")");
        assertThat(superRule).as("superadmin must be SUPER_ADMIN only").isGreaterThanOrEqualTo(0);
        assertThat(adminRule).as("admin must be ADMIN or SUPER_ADMIN").isGreaterThanOrEqualTo(0);
        assertThat(superRule)
                .as("the more specific superadmin rule must be declared before /api/admin/**")
                .isLessThan(adminRule);
    }

    @Test
    void noAdminApiIsPubliclyPermitted() {
        // admin.html is a public STATIC page, but every /api/admin/** call must
        // still be gated. Guard against an admin path leaking into permitAll().
        String permitBlock = securityConfig.substring(securityConfig.indexOf(".permitAll()"));
        assertThat(permitBlock)
                .as("no admin or superadmin API may sit inside the permitAll block")
                .doesNotContain("/api/admin/")
                .doesNotContain("/api/superadmin/");
    }

    @Test
    void anonymousAdminRequestsGet401NotAServerError() {
        // An unauthenticated admin call must be rejected cleanly, not with a 500.
        assertThat(securityConfig)
                .as("an authentication entry point returns 401 for anonymous callers")
                .contains("AUTHENTICATION_REQUIRED")
                .contains("response.setStatus(401)");
    }

    @Test
    void httpBasicStaysDisabledSoNoAlternateAdminLoginPathExists() {
        assertThat(securityConfig)
                .as("only the existing session mechanism may authenticate an Admin")
                .contains("httpBasic(basic -> basic.disable())");
    }

    @Test
    void theAdminGateStillChecksTheServerReportedRole() {
        // Authorization must not rest on a client-side label.
        int start = adminJs.indexOf("async function adminGate()");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(adminJs.substring(start, start + 700))
                .as("gate reads the role from /api/auth/me")
                .contains("api('/api/auth/me')")
                .contains("me.role === 'ADMIN' || me.role === 'SUPER_ADMIN'");
    }

    @Test
    void superAdminOnlyConsoleTabIsStillHiddenFromAdmins() {
        assertThat(adminJs)
                .as("the Console tab must stay Super Admin only")
                .contains("A.role === 'SUPER_ADMIN'");
    }
}
