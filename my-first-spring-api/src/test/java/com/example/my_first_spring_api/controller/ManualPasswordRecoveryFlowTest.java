package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.PasswordResetRequest;
import com.example.my_first_spring_api.model.PasswordResetRequestStatus;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.PasswordResetRequestRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.example.my_first_spring_api.service.PasswordRecoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

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
 * Manual password recovery V1 - request and Super Admin review half of the flow:
 * neutral request creation, hierarchy (Area Admin cannot approve), temp password
 * generation shown once and hashed, rejection, and super-admin-to-super-admin.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:manual-recovery-flow;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.auth.password-login-enabled=true",
})
@ActiveProfiles("test")
class ManualPasswordRecoveryFlowTest {

    private static final String ADMIN_TEST_PASSWORD = newTestPassword();
    private static final String SUPER_ADMIN_TEST_PASSWORD = newTestPassword();

    @DynamicPropertySource
    static void registerAdminPasswordProperties(DynamicPropertyRegistry registry) {
        registry.add("sociomart.auth.admin-password", () -> ADMIN_TEST_PASSWORD);
        registry.add("sociomart.auth.super-admin-password", () -> SUPER_ADMIN_TEST_PASSWORD);
    }

    private static String newTestPassword() {
        return UUID.randomUUID().toString().replace("-", "") + "Aa1!";
    }

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

    private String uniqueMobile(String prefix) {
        // Digits only: the login DTO enforces a 10-digit mobile.
        StringBuilder sb = new StringBuilder(prefix);
        java.util.Random random = new java.util.Random();
        while (sb.length() < 10) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private User seller(String mobile, String password) {
        User u = new User("Seller " + suffix, mobile, "S-1", UserRole.SELLER);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        u = users.save(u);
        kitchens.save(new Kitchen("k-" + suffix, "Kitchen " + suffix, "", null, u));
        return u;
    }

    private User platformUser(UserRole role, String mobile) {
        return users.save(new User(role.name() + " " + suffix, mobile, null, role));
    }

    private MockHttpSession login(String mobile, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("mobileNumber", mobile, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long createRequestFor(String mobile) throws Exception {
        mvc.perform(post("/api/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("mobileNumber", mobile))))
                .andExpect(status().isOk());
        return requests.findAll().stream()
                .filter(r -> r.getUser().getMobileNumber().equals(mobile))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void sellerRecoveryRequestIsNeutralPersistedAndAudited() throws Exception {
        String mobile = uniqueMobile("91");
        seller(mobile, newTestPassword());

        mvc.perform(post("/api/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("mobileNumber", mobile))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(PasswordRecoveryService.NEUTRAL_REQUEST_MESSAGE));

        PasswordResetRequest row = requests.findAll().stream()
                .filter(r -> r.getUser().getMobileNumber().equals(mobile)).findFirst().orElseThrow();
        assertThat(row.getStatus()).isEqualTo(PasswordResetRequestStatus.PENDING);
        assertThat(row.getTempPasswordHash()).isNull();
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    void unknownAndBuyerMobilesAreNeutralAndCreateNothing() throws Exception {
        String buyerMobile = uniqueMobile("92");
        users.save(new User("Buyer " + suffix, buyerMobile, null, UserRole.BUYER));
        String unknownMobile = uniqueMobile("93");

        for (String mobile : new String[]{buyerMobile, unknownMobile}) {
            mvc.perform(post("/api/auth/forgot-password").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(Map.of("mobileNumber", mobile))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(PasswordRecoveryService.NEUTRAL_REQUEST_MESSAGE));
        }
        assertThat(requests.findAll())
                .noneMatch(r -> r.getUser().getMobileNumber().equals(buyerMobile)
                        || r.getUser().getMobileNumber().equals(unknownMobile));
    }

    @Test
    void areaAdminCanViewButCannotGenerateOrReject() throws Exception {
        String sellerMobile = uniqueMobile("94");
        seller(sellerMobile, newTestPassword());
        Long requestId = createRequestFor(sellerMobile);
        MockHttpSession adminSession = login(platformUser(UserRole.ADMIN, uniqueMobile("96")).getMobileNumber(),
                ADMIN_TEST_PASSWORD);

        mvc.perform(get("/api/admin/password-recovery").session(adminSession))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/password-recovery/" + requestId + "/generate-temp-password")
                        .with(csrf()).session(adminSession))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/password-recovery/" + requestId + "/reject")
                        .with(csrf()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("reason", "no"))))
                .andExpect(status().isForbidden());
        assertThat(requests.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(PasswordResetRequestStatus.PENDING);
    }

    @Test
    void superAdminGeneratesTempPasswordShownOnceAndStoredHashed() throws Exception {
        String sellerMobile = uniqueMobile("97");
        seller(sellerMobile, newTestPassword());
        Long requestId = createRequestFor(sellerMobile);
        MockHttpSession superSession = login(platformUser(UserRole.SUPER_ADMIN, uniqueMobile("98")).getMobileNumber(),
                SUPER_ADMIN_TEST_PASSWORD);

        MvcResult generated = mvc.perform(post(
                        "/api/admin/password-recovery/" + requestId + "/generate-temp-password")
                        .with(csrf()).session(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tempPassword").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.warning").isNotEmpty())
                .andReturn();
        String tempPassword = (String) objectMapper.readTree(generated.getResponse().getContentAsString())
                .get("tempPassword").asText();
        assertThat(tempPassword).hasSizeGreaterThanOrEqualTo(10);

        PasswordResetRequest row = requests.findById(requestId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(PasswordResetRequestStatus.APPROVED);
        assertThat(row.getTempPasswordHash()).isNotEqualTo(tempPassword);
        assertThat(passwordEncoder.matches(tempPassword, row.getTempPasswordHash())).isTrue();
        assertThat(row.getReviewer()).isNotNull();
        assertThat(row.getTempPasswordExpiresAt()).isAfter(java.time.LocalDateTime.now());

        // A second generation attempt must fail: the request is no longer PENDING.
        mvc.perform(post("/api/admin/password-recovery/" + requestId + "/generate-temp-password")
                        .with(csrf()).session(superSession))
                .andExpect(status().isConflict());
    }

    @Test
    void superAdminRejectsRequestAndItCannotBeApprovedAfterwards() throws Exception {
        String sellerMobile = uniqueMobile("99");
        seller(sellerMobile, newTestPassword());
        Long requestId = createRequestFor(sellerMobile);
        MockHttpSession superSession = login(platformUser(UserRole.SUPER_ADMIN, uniqueMobile("90")).getMobileNumber(),
                SUPER_ADMIN_TEST_PASSWORD);

        mvc.perform(post("/api/admin/password-recovery/" + requestId + "/reject")
                        .with(csrf()).session(superSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("reason", "Identity could not be verified"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(requests.findById(requestId).orElseThrow().getRejectionReason())
                .isEqualTo("Identity could not be verified");

        mvc.perform(post("/api/admin/password-recovery/" + requestId + "/generate-temp-password")
                        .with(csrf()).session(superSession))
                .andExpect(status().isConflict());
    }

    @Test
    void superAdminCanResetAnotherSuperAdminThroughTheSameReview() throws Exception {
        String victimMobile = uniqueMobile("88");
        platformUser(UserRole.SUPER_ADMIN, victimMobile);
        Long requestId = createRequestFor(victimMobile);
        MockHttpSession superSession = login(platformUser(UserRole.SUPER_ADMIN, uniqueMobile("89")).getMobileNumber(),
                SUPER_ADMIN_TEST_PASSWORD);

        MvcResult generated = mvc.perform(post(
                        "/api/admin/password-recovery/" + requestId + "/generate-temp-password")
                        .with(csrf()).session(superSession))
                .andExpect(status().isOk())
                .andReturn();
        String tempPassword = (String) objectMapper.readTree(generated.getResponse().getContentAsString())
                .get("tempPassword").asText();

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", victimMobile, "password", tempPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

}