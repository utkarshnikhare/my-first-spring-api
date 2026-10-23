package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.UserRepository;
import com.example.my_first_spring_api.service.LocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Phase D security hardening: HTTP-level evidence for the controls that protect
 * sessions and the deployment surface - blocked accounts, suspended sellers,
 * developer tools outside the "local" profile, security headers, CSRF on the
 * auth endpoints and production logging levels. The rate limiter stays OFF here
 * (test-profile default) so these flows are never coupled through it.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security-hardening;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SecurityHardeningHttpTest {

    private static final String PASSWORD = UUID.randomUUID().toString().replace("-", "") + "Aa1!";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository users;
    @Autowired private LocationService locations;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private Long areaId;
    private Long societyId;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        var area = locations.createArea("Hardening Area " + suffix);
        var society = locations.createSociety(area.getId(), "Hardening Society " + suffix);
        areaId = area.getId();
        societyId = society.getId();
    }

    @Test
    void blockedAccountLosesAccessImmediatelyButCanStillReadItsStateAndLogOut() throws Exception {
        String mobile = uniqueMobile();
        MockHttpSession session = registerBuyerSession(mobile);
        login(mobile, PASSWORD); // a second real session, as a reloaded SPA tab would have

        // Block the account exactly as AdminService.blockBuyer does.
        User buyer = users.findByMobileNumber(mobile).orElseThrow();
        buyer.setBlocked(true);
        buyer.setBlockedReason("Phase D hardening test");
        users.saveAndFlush(buyer);

        // Escape hatch: /api/auth/** stays reachable so the client can read the
        // block state - without being shown any profile data - and log out.
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.message").value("Your account is blocked"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.mobileNumber").doesNotExist());

        // Every other path is refused immediately with 403 and the agreed message.
        mvc.perform(get("/api/discovery/counts").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is blocked"));

        // Re-login is refused with the same generic 401 as a wrong password, so
        // this endpoint stays useless for probing whether an account is blocked.
        login(mobile, PASSWORD).andExpect(status().isUnauthorized());

        // Logout works through the escape hatch and ends the session cleanly.
        mvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void sellerWithoutApprovedStatusCannotUseTheSellerApi() throws Exception {
        String mobile = uniqueMobile();
        MockHttpSession session = registerSellerSession(mobile);
        login(mobile, PASSWORD);

        // Suspend the seller as AdminService.suspendSeller does; the session
        // filter re-derives authorities per request, so the very next call
        // already sees SELLER_PENDING instead of SELLER.
        User seller = users.findByMobileNumber(mobile).orElseThrow();
        seller.setSellerApprovalStatus(SellerApprovalStatus.SUSPENDED);
        users.saveAndFlush(seller);

        mvc.perform(get("/api/seller/kitchen").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/seller-app/dashboard").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void developerToolsAreDeniedOutsideTheLocalProfile() throws Exception {
        // The "test" profile (like prod) does not include "local", so the access
        // rule denies the H2 console and the OpenAPI/Swagger surface outright -
        // anonymous callers get the JSON 401 entry point, not a tool UI.
        mvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersAreAppliedToEveryResponse() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
        // HSTS is deliberately absent: TLS terminates at the Render proxy and the
        // app never sees https, so emitting HSTS here would be misleading.
    }

    @Test
    void unsafeAuthRequestsWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", "9999999999", "password", "no-token-pass"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register/buyer").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("name", "No Token"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicationAndProdLoggingStayAtInfoOrAbove() throws Exception {
        assertLoggingIsNotLoweredThanInfo("application.properties");
        assertLoggingIsNotLoweredThanInfo("application-prod.properties");
        // application-local.properties may use DEBUG - that is its purpose.
    }

    /**
     * Reads a properties file straight from the classpath and asserts no
     * {@code logging.level.*} entry lowers the application below INFO. This is a
     * plain config check: it fails the moment someone ships a DEBUG/TRACE level
     * in the base or production configuration.
     */
    private static void assertLoggingIsNotLoweredThanInfo(String resource) throws Exception {
        Properties properties = new Properties();
        try (var input = new ClassPathResource(resource).getInputStream()) {
            properties.load(input);
        }
        properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith("logging.level."))
                .forEach(name -> assertThat(properties.getProperty(name))
                        .as("%s must not lower %s below INFO", resource, name)
                        .isIn("INFO", "WARN", "ERROR"));
    }

    /** Registers a buyer over HTTP and returns the real login session. */
    private MockHttpSession registerBuyerSession(String mobile) throws Exception {
        MvcResult registered = mvc.perform(post("/api/auth/register/buyer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "name", "Hardening Buyer " + suffix,
                                "mobileNumber", mobile,
                                "password", PASSWORD,
                                "flatHouseNumber", "HB-" + suffix,
                                "building", "HB Wing",
                                "areaId", areaId,
                                "societyId", societyId))))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) registered.getRequest().getSession(false);
    }

    /** Registers a seller with a pending storefront and returns the login session. */
    private MockHttpSession registerSellerSession(String mobile) throws Exception {
        MvcResult registered = mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.ofEntries(
                                Map.entry("sellerName", "Hardening Seller " + suffix),
                                Map.entry("mobileNumber", mobile),
                                Map.entry("password", PASSWORD),
                                Map.entry("whatsappNumber", uniqueMobile()),
                                Map.entry("alternateContact", uniqueMobile()),
                                Map.entry("kitchenName", "Hardening Kitchen " + suffix),
                                Map.entry("kitchenSlug", "hardening-kitchen-" + suffix),
                                Map.entry("speciality", "Maharashtrian breakfast"),
                                Map.entry("sellerCategory", "BOTH"),
                                Map.entry("shortDescription", "Phase D hardening test storefront"),
                                Map.entry("instagramLink", "https://instagram.com/hardening-test"),
                                Map.entry("primaryAreaId", areaId),
                                Map.entry("primarySocietyId", societyId),
                                Map.entry("building", "HS Wing"),
                                Map.entry("serviceAreaId", areaId),
                                Map.entry("serviceSocietyIds", List.of(societyId))))))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) registered.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions login(String mobile, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "mobileNumber", mobile, "password", password))));
    }

    private static String uniqueMobile() {
        return "5" + String.format("%09d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000_000);
    }
}
