package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.UserRepository;
import com.example.my_first_spring_api.service.LocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static com.example.my_first_spring_api.SessionConfig.SESSION_COOKIE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Phase E: persistent JDBC-backed sessions. Every test goes through the real
 * HTTP filter chain (including Spring Session's filter) with a browser-style
 * session cookie, then proves the evidence straight from the database: the row
 * in SPRING_SESSION*, re-materialization from the cookie alone, row deletion on
 * logout, the 30-minute timeout and the Secure/SameSite=Lax cookie.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jdbc-session-persist;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class JdbcSessionPersistenceTest {

    private static final String PASSWORD = UUID.randomUUID().toString().replace("-", "") + "Aa1!";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SessionRepository<?> sessionRepository;
    @Autowired private UserRepository users;
    @Autowired private LocationService locations;
    @Autowired private Environment environment;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private Long areaId;
    private Long societyId;

    @BeforeEach
    void setUp() {
        // Order matters: the Spring Session filter must wrap the request BEFORE
        // the Spring Security chain and the controllers, exactly like the
        // DelegatingFilterProxyRegistrationBean does on the real server.
        SessionRepositoryFilter<?> sessionFilter =
                webApplicationContext.getBean(SessionRepositoryFilter.class);
        mvc = webAppContextSetup(webApplicationContext).addFilters(sessionFilter)
                .apply(springSecurity()).build();
        var area = locations.createArea("Session Area " + suffix);
        var society = locations.createSociety(area.getId(), "Session Society " + suffix);
        areaId = area.getId();
        societyId = society.getId();
    }

    @Test
    void loginCreatesUsableAuthenticatedSession() throws Exception {
        String mobile = uniqueMobile();
        registerBuyer(mobile);
        Cookie cookie = loginSessionCookie(mobile);

        mvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.mobileNumber").value(mobile));
    }

    @Test
    void sessionIsStoredInTheJdbcSessionTables() throws Exception {
        String mobile = uniqueMobile();
        registerBuyer(mobile);
        Cookie cookie = loginSessionCookie(mobile);

        assertThat(sessionRowCount(cookie.getValue())).isEqualTo(1);
        String principal = jdbcTemplate.queryForObject(
                "SELECT PRINCIPAL_NAME FROM SPRING_SESSION WHERE SESSION_ID = ?",
                String.class, cookie.getValue());
        assertThat(principal).isNotBlank();
        Integer securityContextRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES s JOIN SPRING_SESSION ON "
                        + "SPRING_SESSION.PRIMARY_ID = s.SESSION_PRIMARY_ID "
                        + "WHERE SPRING_SESSION.SESSION_ID = ? "
                        + "AND s.ATTRIBUTE_NAME = 'SPRING_SECURITY_CONTEXT'",
                Integer.class, cookie.getValue());
        assertThat(securityContextRows).isEqualTo(1);
    }

    @Test
    void sessionIsRecoveredFromTheDatabaseAfterTheOriginalRequestEnds() throws Exception {
        String mobile = uniqueMobile();
        registerBuyer(mobile);
        Cookie cookie = loginSessionCookie(mobile);
        // The original request and its session object are gone here: only the
        // cookie value survives, exactly like a browser tab after a restart.
        // A fresh request presenting only the cookie must re-materialize the
        // same authenticated session from the database row.
        mvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.mobileNumber").value(mobile));

        var recovered = sessionRepository.findById(cookie.getValue());
        Session session = recovered;
        assertThat(session).isNotNull();
        Object securityContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(securityContext).isNotNull();
        assertThat(session.getMaxInactiveInterval().getSeconds()).isEqualTo(30 * 60);
    }

    @Test
    void logoutInvalidatesThePersistedSession() throws Exception {
        String mobile = uniqueMobile();
        registerBuyer(mobile);
        Cookie cookie = loginSessionCookie(mobile);
        assertThat(sessionRowCount(cookie.getValue())).isEqualTo(1);

        mvc.perform(post("/api/auth/logout").cookie(cookie).with(csrf()))
                .andExpect(status().isOk());

        assertThat(sessionRowCount(cookie.getValue())).isEqualTo(0);
        mvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void sessionCookieIsSecureSameSiteLaxAndTimeoutIsThirtyMinutes() throws Exception {
        // The pre-Phase-E container cookie carried Secure + SameSite=Lax; the
        // Spring Session cookie must carry exactly the same flags.
        assertThat(environment.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(environment.getProperty("server.servlet.session.timeout")).isEqualTo("30m");

        String mobile = uniqueMobile();
        registerBuyer(mobile);
        MvcResult loggedIn = login(mobile, PASSWORD).andExpect(status().isOk()).andReturn();

        Cookie cookie = loggedIn.getResponse().getCookie(SESSION_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        String setCookie = loggedIn.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("HttpOnly");

        // The effective timeout is what the persisted session enforces...
        Integer maxInactive = jdbcTemplate.queryForObject(
                "SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE SESSION_ID = ?",
                Integer.class, cookie.getValue());
        assertThat(maxInactive).isEqualTo(30 * 60);
        // ...and what a recovered session reports.
        Session probe = sessionRepository.findById(cookie.getValue());
        assertThat(probe).isNotNull();
        assertThat(probe.getMaxInactiveInterval().getSeconds()).isEqualTo(30 * 60);
    }

    @Test
    void csrfProtectedRequestsWorkWithJdbcBackedSessions() throws Exception {
        String mobile = uniqueMobile();
        registerBuyer(mobile);
        Cookie cookie = loginSessionCookie(mobile);

        // Missing CSRF token: refused, and the persisted session survives.
        mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isForbidden());
        assertThat(sessionRowCount(cookie.getValue())).isEqualTo(1);

        // Valid CSRF token against the SAME persisted session: accepted.
        mvc.perform(post("/api/auth/logout").cookie(cookie).with(csrf()))
                .andExpect(status().isOk());
        assertThat(sessionRowCount(cookie.getValue())).isEqualTo(0);
    }

    @Test
    void blockedUserCannotRegainAccessThroughThePersistedSession() throws Exception {
        String mobile = uniqueMobile();
        registerBuyer(mobile);
        Cookie cookie = loginSessionCookie(mobile);
        assertThat(sessionRowCount(cookie.getValue())).isEqualTo(1);

        // Block the account exactly as AdminService.blockBuyer does. The row
        // stays in the JDBC session tables - persistence must not launder it.
        User buyer = users.findByMobileNumber(mobile).orElseThrow();
        buyer.setBlocked(true);
        buyer.setBlockedReason("Phase E session persistence test");
        users.saveAndFlush(buyer);

        mvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.message").value("Your account is blocked"));
        mvc.perform(get("/api/discovery/counts").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is blocked"));

        assertThat(sessionRowCount(cookie.getValue())).isEqualTo(1);
    }

    /** Registers a buyer over HTTP (201); registration also logs the buyer in. */
    private void registerBuyer(String mobile) throws Exception {
        mvc.perform(post("/api/auth/register/buyer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "name", "Session Buyer " + suffix,
                                "mobileNumber", mobile,
                                "password", PASSWORD,
                                "flatHouseNumber", "SB-" + suffix,
                                "building", "SB Wing",
                                "areaId", areaId,
                                "societyId", societyId))))
                .andExpect(status().isCreated());
    }

    /** Logs in over HTTP and returns the browser-style session cookie. */
    private Cookie loginSessionCookie(String mobile) throws Exception {
        MvcResult loggedIn = login(mobile, PASSWORD).andExpect(status().isOk()).andReturn();
        Cookie cookie = loggedIn.getResponse().getCookie(SESSION_COOKIE_NAME);
        if (cookie == null) {
            // Fall back to the request's session object if MockMvc did not
            // surface the Set-Cookie header: the value still identifies the
            // same persisted row.
            MockHttpSession session = (MockHttpSession) loggedIn.getRequest().getSession(false);
            assertThat(session).as("login must create an HTTP session").isNotNull();
            cookie = new Cookie(SESSION_COOKIE_NAME, session.getId());
        }
        return cookie;
    }

    private org.springframework.test.web.servlet.ResultActions login(String mobile, String password)
            throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "mobileNumber", mobile, "password", password))));
    }

    private int sessionRowCount(String sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, sessionId);
        assertThat(count).isNotNull();
        return count;
    }

    private static String uniqueMobile() {
        return "6" + String.format("%09d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000_000);
    }
}
