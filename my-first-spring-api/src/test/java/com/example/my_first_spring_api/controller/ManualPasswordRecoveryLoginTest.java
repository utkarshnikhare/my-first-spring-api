package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.PasswordResetRequest;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.PasswordResetRequestRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Manual password recovery V1 - login half of the flow: first use of the
 * temporary password, the forced-change session gate, change-password
 * validation and completion, single-use enforcement and 24-hour expiry.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:manual-recovery-login;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.auth.password-login-enabled=true",
})
@ActiveProfiles("test")
class ManualPasswordRecoveryLoginTest {

    private static final String SUPER_ADMIN_TEST_PASSWORD = UUID.randomUUID()
            .toString().replace("-", "") + "Aa1!";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PasswordResetRequestRepository requests;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private static String newTestPassword() {
        return UUID.randomUUID().toString().replace("-", "") + "Aa1!";
    }

    private String uniqueMobile(String prefix) {
        // Digits only: the login DTO enforces a 10-digit mobile.
        StringBuilder sb = new StringBuilder(prefix);
        java.util.Random random = new java.util.Random();
        while (sb.length() < 10) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private User approvedSeller(String mobile, String password) {
        User u = new User("Seller " + suffix, mobile, "S-1", UserRole.SELLER);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        u = users.save(u);
        kitchens.save(new Kitchen("k-" + suffix, "Kitchen " + suffix, "", null, u));
        return u;
    }

    /** Creates a PENDING request, generates the temp password as Super Admin, returns it. */
    private String issueTempPassword(String sellerMobile) throws Exception {
        PasswordResetRequest row = requests.save(new PasswordResetRequest(
                users.findByMobileNumber(sellerMobile).orElseThrow()));
        User superAdmin = users.save(new User("SuperAdmin " + suffix, uniqueMobile("9"), null, UserRole.SUPER_ADMIN));
        Map<String, Object> out = new com.example.my_first_spring_api.service.PasswordRecoveryService(
                requests, users, null, passwordEncoder, null, null)
                .generateTempPassword(row.getId(), superAdmin);
        return (String) out.get("tempPassword");
    }

    private MockHttpSession loginExpectingOk(String mobile, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("mobileNumber", mobile, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MvcResult loginRaw(String mobile, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("mobileNumber", mobile, "password", password))))
                .andReturn();
    }

    @Test
    void tempPasswordLogsInOnceAndForcesChangeBlockingEverythingElse() throws Exception {
        String mobile = uniqueMobile("91");
        approvedSeller(mobile, newTestPassword());
        String tempPassword = issueTempPassword(mobile);

        MvcResult loggedIn = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", mobile, "password", tempPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loggedIn.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("MUST_CHANGE_PASSWORD")).isEqualTo(Boolean.TRUE);

        // The health probe stays reachable; every other API is blocked until the
        // password is changed. (Logout goes last - it invalidates the session.)
        mvc.perform(get("/api/health").session(session)).andExpect(status().isOk());
        MvcResult blocked = mvc.perform(get("/api/seller-app/dashboard").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"))
                .andReturn();
        assertThat(blocked.getResponse().getContentAsString()).contains("must change your password");
        mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isOk());
    }

    @Test
    void changePasswordValidatesLengthConfirmationAndCurrentPassword() throws Exception {
        String mobile = uniqueMobile("92");
        approvedSeller(mobile, newTestPassword());
        String tempPassword = issueTempPassword(mobile);
        MockHttpSession session = loginExpectingOk(mobile, tempPassword);

        // Too short (DTO-level 400).
        mvc.perform(post("/api/auth/change-password").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "currentPassword", tempPassword, "newPassword", "short",
                                "confirmPassword", "short"))))
                .andExpect(status().isBadRequest());
        // Confirmation mismatch (service-level 400).
        mvc.perform(post("/api/auth/change-password").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "currentPassword", tempPassword,
                                "newPassword", "BrandNewPassw0rd!",
                                "confirmPassword", "DifferentPassw0rd!"))))
                .andExpect(status().isBadRequest());
        // Wrong current password (401).
        mvc.perform(post("/api/auth/change-password").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "currentPassword", "TotallyWrong123!",
                                "newPassword", "BrandNewPassw0rd!",
                                "confirmPassword", "BrandNewPassw0rd!"))))
                .andExpect(status().isUnauthorized());
        // Still forced: nothing was changed.
        mvc.perform(get("/api/seller-app/dashboard").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void completingTheChangeUnlocksTheSessionAndInvalidatesTheTempPassword() throws Exception {
        String mobile = uniqueMobile("93");
        approvedSeller(mobile, newTestPassword());
        String tempPassword = issueTempPassword(mobile);
        MockHttpSession session = loginExpectingOk(mobile, tempPassword);
        String newPassword = "BrandNewPassw0rd!";

        mvc.perform(post("/api/auth/change-password").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "currentPassword", tempPassword,
                                "newPassword", newPassword,
                                "confirmPassword", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully. You can now continue normally."));
        assertThat(session.getAttribute("MUST_CHANGE_PASSWORD")).isNull();

        // The session is unlocked immediately.
        mvc.perform(get("/api/seller-app/dashboard").session(session)).andExpect(status().isOk());

        // The temporary password is dead (single use), the new one works normally.
        assertThat(loginRaw(mobile, tempPassword).getResponse().getStatus()).isEqualTo(401);
        MvcResult relogin = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", mobile, "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();
        MockHttpSession fresh = (MockHttpSession) relogin.getRequest().getSession(false);
        mvc.perform(get("/api/seller-app/dashboard").session(fresh)).andExpect(status().isOk());
    }

    @Test
    void expiredTempPasswordCanNoLongerLogIn() throws Exception {
        String mobile = uniqueMobile("94");
        approvedSeller(mobile, newTestPassword());
        String tempPassword = issueTempPassword(mobile);
        PasswordResetRequest row = requests.findAll().stream()
                .filter(r -> r.getUser().getMobileNumber().equals(mobile))
                .findFirst().orElseThrow();
        row.setTempPasswordExpiresAt(LocalDateTime.now().minusHours(1));
        requests.save(row);

        assertThat(loginRaw(mobile, tempPassword).getResponse().getStatus()).isEqualTo(401);
        assertThat(requests.findById(row.getId()).orElseThrow().isTempPasswordUsed()).isFalse();
    }

    @Test
    void buyersAreExcludedFromTempPasswordsEvenWithAValidHash() throws Exception {
        // Strict rule: buyers keep mobile OTP; no password/reset flow reaches them.
        String buyerMobile = uniqueMobile("95");
        User buyer = users.save(new User("Buyer " + suffix, buyerMobile, null, UserRole.BUYER));
        PasswordResetRequest row = requests.save(new PasswordResetRequest(buyer));
        row.setStatus(com.example.my_first_spring_api.model.PasswordResetRequestStatus.APPROVED);
        row.setTempPasswordHash(passwordEncoder.encode("SomeTempPassw0rd!"));
        row.setTempPasswordExpiresAt(LocalDateTime.now().plusHours(24));
        requests.save(row);

        assertThat(loginRaw(buyerMobile, "SomeTempPassw0rd!").getResponse().getStatus()).isEqualTo(401);
    }

}