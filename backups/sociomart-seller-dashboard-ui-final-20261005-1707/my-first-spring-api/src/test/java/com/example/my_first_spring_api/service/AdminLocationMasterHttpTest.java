package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.SocietyRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * HTTP-level regression tests for the Admin Area and Society master.
 *
 * <p><b>Why this class exists.</b> AdminLocationsManagementTest drives
 * adminService.createArea(...) directly, so it never touches the Spring Security
 * filter chain. Every other test in this project is service-level too, which is
 * exactly how a real defect survived: with CSRF enabled (any profile other than
 * demo/dev/default - including "test" and "prod") the frontend sent no CSRF
 * token, so the CSRF filter rejected every state-changing Admin request with
 * 403 Forbidden BEFORE authorization was evaluated, while every GET on the same
 * screen kept working. These tests reproduce that over real HTTP.</p>
 *
 * <p><b>Browser emulation.</b> The Browser helper carries the session and the
 * XSRF-TOKEN cookie forward between requests, which is what a browser does and
 * what the fixed api() in common.js now does.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-location-http-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class AdminLocationMasterHttpTest {

    @Autowired private WebApplicationContext ctx;
    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private SocietyRepository societyRepository;

    private MockMvc mvc;
    private String sfx;
    private int mobileSeq;
    /** Emulates one browser tab: keeps the session and the CSRF cookie in sync. */
    private final class Browser {
        MockHttpSession session;
        String csrf;

        MockHttpServletRequestBuilder req(MockHttpServletRequestBuilder b) {
            if (session != null) b = b.session(session);
            if (csrf != null) {
                b = b.cookie(new Cookie("XSRF-TOKEN", csrf))
                         .header("X-XSRF-TOKEN", csrf);
            }
            return b;
        }

        MvcResult call(MockHttpServletRequestBuilder b) throws Exception {
            MvcResult r = mvc.perform(req(b)).andReturn();
            // sessionFixation().migrateSession() replaces the session on the first
            // authenticated request, so adopt whatever the server actually used.
            Object used = r.getRequest().getSession(false);
            if (used != null) session = (MockHttpSession) used;
            // the server mints a new token whenever the request carried no cookie
            Cookie[] cookies = r.getResponse().getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("XSRF-TOKEN".equals(c.getName()) && c.getValue() != null) csrf = c.getValue();
                }
            }
            return r;
        }
    }

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(ctx).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        sfx = UUID.randomUUID().toString().substring(0, 8);
        mobileSeq = 0;
    }

    private String mobile() {
        int seed = (sfx.hashCode() & 0x7fffffff) + (++mobileSeq) * 7_919;
        return "9" + String.format("%09d", seed % 1_000_000_000);
    }

    private Browser browserAs(UserRole role) {
        User u = userRepository.save(new User("Http " + role, mobile(), null, role));
        Browser b = new Browser();
        b.session = new MockHttpSession();
        b.session.setAttribute("BUYER_USER", u.getId());
        return b;
    }

    private static int status(MvcResult r) { return r.getResponse().getStatus(); }

    private static String body(MvcResult r) throws Exception {
        String s = r.getResponse().getContentAsString();
        return s == null ? "" : s.replaceAll("\\s+", " ");
    }

    private static long idOf(MvcResult r, String key) throws Exception {
        String b = body(r);
        int i = b.indexOf("\"" + key + "\":");
        if (i < 0) throw new AssertionError("no " + key + " in " + b);
        int j = b.indexOf(',', i);
        String v = j < 0 ? b.substring(i + key.length() + 3) : b.substring(i + key.length() + 3, j);
        return Long.parseLong(v.trim());
    }
    // ==================== A. the regression that was broken ====================

    @Test
    @DisplayName("REGRESSION: server issues XSRF-TOKEN and Admin Add Area works with it")
    void adminAddAreaWorksOverHttpWithCsrfToken() throws Exception {
        Browser admin = browserAs(UserRole.ADMIN);

        MvcResult seed = admin.call(get("/api/admin/locations"));
        assertThat(status(seed)).as("Admin screen loads (GET works)").isEqualTo(200);
        assertThat(admin.csrf).as("server must publish an XSRF-TOKEN cookie").isNotNull();

        MvcResult created = admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Http Area " + sfx + "\"}"));
        assertThat(status(created)).as("Add Area with CSRF token: " + body(created)).isEqualTo(200);
        assertThat(body(created)).contains("Http Area " + sfx);
    }

    @Test
    @DisplayName("SECURITY NOT WEAKENED: unsafe request without the token is still refused")
    void unsafeRequestWithoutCsrfTokenIsStillRejected() throws Exception {
        Browser admin = browserAs(UserRole.ADMIN);
        admin.call(get("/api/admin/locations"));

        MvcResult noToken = mvc.perform(post("/api/admin/areas")
                        .session(admin.session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Token " + sfx + "\"}"))
                .andReturn();
        assertThat(status(noToken)).isEqualTo(403);
    }

    // ==================== B. Area CRUD ====================

    @Test
    @DisplayName("Area: add, duplicate rejected, rename, disable, re-enable")
    void areaCrudOverHttp() throws Exception {
        Browser admin = browserAs(UserRole.ADMIN);
        admin.call(get("/api/admin/locations"));

        MvcResult added = admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Crud Area " + sfx + "\"}"));
        assertThat(status(added)).isEqualTo(200);
        long areaId = idOf(added, "id");

        MvcResult dup = admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Crud Area " + sfx + "\"}"));
        assertThat(status(dup)).as("duplicate Area rejected: " + body(dup)).isEqualTo(400);

        assertThat(status(admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))))
                .as("empty Area name rejected").isEqualTo(400);
        assertThat(status(admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))))
                .as("whitespace-only Area name rejected").isEqualTo(400);

        MvcResult renamed = admin.call(patch("/api/admin/areas/" + areaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Crud Area Renamed " + sfx + "\"}"));
        assertThat(status(renamed)).isEqualTo(200);
        assertThat(body(renamed)).contains("Crud Area Renamed " + sfx);

        assertThat(status(admin.call(patch("/api/admin/areas/" + areaId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")))).isEqualTo(200);
        assertThat(status(admin.call(patch("/api/admin/areas/" + areaId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")))).isEqualTo(200);
    }
    // ==================== C. Society CRUD ====================

    @Test
    @DisplayName("Society: add under Area, wrong/nonexistent Area, duplicate, rename, disable, re-enable")
    void societyCrudOverHttp() throws Exception {
        Browser admin = browserAs(UserRole.ADMIN);
        admin.call(get("/api/admin/locations"));

        long areaA = idOf(admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Soc A " + sfx + "\"}")), "id");
        long areaB = idOf(admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Soc B " + sfx + "\"}")), "id");

        MvcResult added = admin.call(post("/api/admin/societies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaId\":" + areaA + ",\"name\":\"Soc One " + sfx + "\"}"));
        assertThat(status(added)).as("add Society under Area: " + body(added)).isEqualTo(200);
        long socId = idOf(added, "id");
        assertThat(societyRepository.findById(socId).orElseThrow().getArea().getId())
                .as("society.areaId is stored, never trusted from the client").isEqualTo(areaA);

        MvcResult sameNameOtherArea = admin.call(post("/api/admin/societies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaId\":" + areaB + ",\"name\":\"Soc One " + sfx + "\"}"));
        assertThat(status(sameNameOtherArea))
                .as("same Society name under a different Area is legitimate").isEqualTo(200);

        MvcResult dup = admin.call(post("/api/admin/societies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaId\":" + areaA + ",\"name\":\"Soc One " + sfx + "\"}"));
        assertThat(status(dup)).as("duplicate Society in same Area: " + body(dup)).isEqualTo(400);

        MvcResult ghost = admin.call(post("/api/admin/societies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaId\":987654321,\"name\":\"Ghost " + sfx + "\"}"));
        assertThat(status(ghost)).as("Society under a nonexistent Area: " + body(ghost)).isEqualTo(400);

        assertThat(status(admin.call(patch("/api/admin/societies/" + socId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Soc One Renamed " + sfx + "\"}")))).isEqualTo(200);
        assertThat(status(admin.call(patch("/api/admin/societies/" + socId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")))).isEqualTo(200);
        assertThat(status(admin.call(patch("/api/admin/societies/" + socId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")))).isEqualTo(200);
    }

    // ==================== D. authorization ====================

    @Test
    @DisplayName("Authorization: anonymous/buyer/seller denied, admin and super admin allowed")
    void authorizationMatrixOverHttp() throws Exception {
        String payload = "{\"name\":\"Authz Area " + sfx + "\"}";

        Browser anon = new Browser();
        assertThat(status(anon.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON).content(payload))))
                .as("anonymous denied").isEqualTo(403);

        Browser buyer = browserAs(UserRole.BUYER);
        buyer.call(get("/api/auth/config"));
        assertThat(status(buyer.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON).content(payload))))
                .as("buyer cannot mutate master locations").isEqualTo(403);

        Browser seller = browserAs(UserRole.SELLER);
        seller.call(get("/api/auth/config"));
        assertThat(status(seller.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON).content(payload))))
                .as("seller cannot mutate master locations").isEqualTo(403);

        Browser admin = browserAs(UserRole.ADMIN);
        admin.call(get("/api/auth/config"));
        assertThat(status(admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON).content(payload))))
                .as("admin allowed").isEqualTo(200);

        Browser superAdmin = browserAs(UserRole.SUPER_ADMIN);
        superAdmin.call(get("/api/auth/config"));
        assertThat(status(superAdmin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Authz Super Area " + sfx + "\"}"))))
                .as("super admin allowed").isEqualTo(200);
    }

    @Test
    @DisplayName("Admin/Super Admin separation is preserved")
    void adminCannotReachSuperAdminEndpoints() throws Exception {
        Browser admin = browserAs(UserRole.ADMIN);
        admin.call(get("/api/auth/config"));
        assertThat(status(admin.call(get("/api/superadmin/admins"))))
                .as("admin is not a super admin").isEqualTo(403);
    }
    // ==================== E. Seller integration ====================

    @Test
    @DisplayName("Admin-created Area/Society reaches Seller; a new Society does not expand coverage")
    void sellerCoverageUsesExplicitSocietyIds() throws Exception {
        Browser admin = browserAs(UserRole.ADMIN);
        admin.call(get("/api/admin/locations"));

        long areaId = idOf(admin.call(post("/api/admin/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Seller Flow Area " + sfx + "\"}")), "id");
        long kingsbury = idOf(admin.call(post("/api/admin/societies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaId\":" + areaId + ",\"name\":\"Kingsbury " + sfx + "\"}")), "id");
        long soho = idOf(admin.call(post("/api/admin/societies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaId\":" + areaId + ",\"name\":\"Soho " + sfx + "\"}")), "id");

        User seller = new User("Seller " + sfx, mobile(), "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);
        Society kingsburySoc = societyRepository.findById(kingsbury).orElseThrow();
        Kitchen kitchen = kitchenRepository.save(
                new Kitchen("k-" + sfx, "Kitchen " + sfx, "", null, seller));
        kitchen.setServedSocieties(new LinkedHashSet<>(Set.of(kingsburySoc)));
        kitchen.setServiceAreas(kingsburySoc.getName());
        kitchenRepository.save(kitchen);

        assertThat(kitchenRepository.findById(kitchen.getId()).orElseThrow().getServedSocieties())
                .as("coverage persists explicit Society IDs, not an Area-wide wildcard")
                .extracting(Society::getId).containsExactly(kingsbury);
        assertThat(kitchenRepository.findById(kitchen.getId()).orElseThrow().getServedSocieties())
                .extracting(Society::getId).doesNotContain(soho);

        long manhattan = idOf(admin.call(post("/api/admin/societies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaId\":" + areaId + ",\"name\":\"Manhattan " + sfx + "\"}")), "id");

        assertThat(kitchenRepository.findById(kitchen.getId()).orElseThrow().getServedSocieties())
                .as("a new Society must NOT silently expand existing seller coverage")
                .extracting(Society::getId)
                .containsExactly(kingsbury);
        assertThat(kitchenRepository.findById(kitchen.getId()).orElseThrow().getServedSocieties())
                .extracting(Society::getId).doesNotContain(manhattan);
    }
}