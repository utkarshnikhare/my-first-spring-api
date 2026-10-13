package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.repository.AdminAuditLogRepository;
import com.example.my_first_spring_api.security.AuthRateLimiter;
import com.example.my_first_spring_api.service.AdminAuditService;
import com.example.my_first_spring_api.service.LocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Phase D security hardening: proves the login/registration rate-limit policy
 * end-to-end over HTTP. The limiter is re-enabled here because the shared
 * "test" profile turns it off (every MockMvc request would otherwise present
 * the same source IP and couple unrelated tests through the shared counters).
 *
 * <p>Isolation: each test uses unique mobile numbers, and every request carries
 * an explicit {@code X-Forwarded-For} header so the per-IP counters of one test
 * never bleed into another. The limiter singleton is scoped to this class's own
 * Spring context, which is separate from every other test class.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-rate-limit;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.auth.password-login-enabled=true",
        "sociomart.ratelimit.enabled=true"
})
@ActiveProfiles("test")
class AuthRateLimitSecurityTest {

    private static final String BUYER_PASSWORD = newPassword();
    private static final String WRONG_PASSWORD = newPassword();

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private LocationService locations;
    @Autowired private AdminAuditLogRepository auditLogs;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long areaId;
    private Long societyId;
    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        var area = locations.createArea("Rate Limit Area " + suffix);
        var society = locations.createSociety(area.getId(), "Rate Limit Society " + suffix);
        areaId = area.getId();
        societyId = society.getId();
    }

    @Test
    void fifthFailedLoginLocksTheAccountAndEvenTheCorrectPasswordThenGets429() throws Exception {
        String mobile = uniqueMobile();
        String ip = uniqueIp("11");
        registerBuyer(ip, mobile).andExpect(status().isCreated());

        for (int i = 0; i < AuthRateLimiter.LOGIN_MAX_FAILURES; i++) {
            login(ip, mobile, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        // The lock tripped as a side effect of recording the 5th failure, so the
        // next attempt is rejected before the password is even looked at - the
        // correct password no longer helps while the lock is in force.
        login(ip, mobile, BUYER_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.status").value(429));

        // The lockout is audited exactly once, as SYSTEM, with the mobile as the
        // target label so an Admin can correlate it with an account.
        var lockouts = auditLogs.findByActionOrderByCreatedAtDesc(AdminAuditService.LOGIN_LOCKED_OUT)
                .stream().filter(a -> mobile.equals(a.getTargetLabel())).toList();
        assertThat(lockouts).hasSize(1);
        assertThat(lockouts.get(0).getActorName()).isEqualTo("SYSTEM");
        assertThat(lockouts.get(0).getTargetType()).isEqualTo("USER");
        assertThat(lockouts.get(0).getNewState()).isEqualTo("LOCKED");
        assertThat(lockouts.get(0).getReason()).contains(ip);
    }

    @Test
    void successfulLoginResetsTheMobileCounterButNeverTheIpCounter() throws Exception {
        String registerIp = uniqueIp("21");
        String mobileA = uniqueMobile();
        String mobileB = uniqueMobile();
        String mobileC = uniqueMobile();
        registerBuyer(registerIp, mobileA).andExpect(status().isCreated());
        registerBuyer(registerIp, mobileB).andExpect(status().isCreated());
        registerBuyer(registerIp, mobileC).andExpect(status().isCreated());

        // Per-mobile: 4 failures + success + 4 more failures stay under the limit.
        // Without the reset, the second batch would trip the lock (4+1 = 5) and
        // the last attempts would answer 429 instead of 401.
        for (int i = 0; i < 4; i++) {
            login(uniqueIp("22"), mobileA, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login(uniqueIp("23"), mobileA, BUYER_PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(uniqueIp("24"), mobileA, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        // Per-IP: one user's success must NOT clear the shared counter. Four
        // failures from B, a success from C, then one more failure from B trips
        // the IP lock (5); if the success had reset the counter the final login
        // would succeed. A shared NAT that trips the limit stays limited.
        String sharedIp = uniqueIp("25");
        for (int i = 0; i < 4; i++) {
            login(sharedIp, mobileB, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login(sharedIp, mobileC, BUYER_PASSWORD).andExpect(status().isOk());
        login(sharedIp, mobileB, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login(sharedIp, mobileC, BUYER_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void registrationIsCappedAtFivePerIpAndOtherIpsAreUnaffected() throws Exception {
        String cappedIp = uniqueIp("31");
        for (int i = 0; i < AuthRateLimiter.REGISTRATION_MAX; i++) {
            registerBuyer(cappedIp, uniqueMobile()).andExpect(status().isCreated());
        }
        registerBuyer(cappedIp, uniqueMobile())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
        registerBuyer(uniqueIp("32"), uniqueMobile()).andExpect(status().isCreated());
    }

    private ResultActions registerBuyer(String ip, String mobile) throws Exception {
        return mvc.perform(post("/api/auth/register/buyer")
                .with(csrf())
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "name", "Rate Limit Buyer " + suffix,
                        "mobileNumber", mobile,
                        "password", BUYER_PASSWORD,
                        "flatHouseNumber", "RL-" + suffix,
                        "building", "RL Wing",
                        "areaId", areaId,
                        "societyId", societyId))));
    }

    private ResultActions login(String ip, String mobile, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(csrf())
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "mobileNumber", mobile, "password", password))));
    }

    private static String uniqueMobile() {
        return "6" + String.format("%09d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000_000);
    }

    /**
     * Documentation-range address (RFC 5737) with a per-call tag, so every call
     * yields a distinct X-Forwarded-For value and the per-IP counters of one
     * test can never leak into another.
     */
    private static String uniqueIp(String tag) {
        int host = Math.abs(UUID.randomUUID().hashCode()) % 254 + 1;
        int uniq = Math.abs(UUID.randomUUID().hashCode()) % 97;
        return "203.0.113." + host + ":" + tag + uniq;
    }

    private static String newPassword() {
        return UUID.randomUUID().toString().replace("-", "") + "Aa1!";
    }
}
