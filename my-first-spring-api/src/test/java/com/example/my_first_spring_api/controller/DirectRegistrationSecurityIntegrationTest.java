package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.example.my_first_spring_api.service.LocationService;
import com.example.my_first_spring_api.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:direct-registration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.demo.direct-auth-enabled=true",
})
@ActiveProfiles("test")
class DirectRegistrationSecurityIntegrationTest {

    private static final String BUYER_TEST_PASSWORD = newTestPassword();
    private static final String SECOND_BUYER_TEST_PASSWORD = newTestPassword();
    private static final String SELLER_TEST_PASSWORD = newTestPassword();
    private static final String ADMIN_TEST_PASSWORD = newTestPassword();
    private static final String SUPER_ADMIN_TEST_PASSWORD = newTestPassword();
    private static final String WRONG_TEST_PASSWORD = newTestPassword();

    @DynamicPropertySource
    static void registerAdminPasswordProperties(DynamicPropertyRegistry registry) {
        registry.add("sociomart.demo.admin-password", () -> ADMIN_TEST_PASSWORD);
        registry.add("sociomart.demo.super-admin-password", () -> SUPER_ADMIN_TEST_PASSWORD);
    }

    private static String newTestPassword() {
        return UUID.randomUUID().toString().replace("-", "") + "Aa1!";
    }

    @Autowired private WebApplicationContext webApplicationContext;
    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private LocationService locations;

    private Long areaId;
    private Long societyId;
    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        var area = locations.createArea("Direct Auth Area " + suffix);
        var society = locations.createSociety(area.getId(), "Direct Auth Society " + suffix);
        areaId = area.getId();
        societyId = society.getId();
    }

    @Test
    void buyerRegistrationPasswordLoginAndAuthorizationAreEnforcedOverHttp() throws Exception {
        String mobile = uniqueMobile("6");
        Map<String, Object> buyer = Map.of(
                "name", "Buyer " + suffix,
                "mobileNumber", mobile,
                "password", BUYER_TEST_PASSWORD,
                "flatHouseNumber", "B-204",
                "building", "B Wing",
                "areaId", areaId,
                "societyId", societyId
        );

        MvcResult registered = mvc.perform(post("/api/auth/register/buyer")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(buyer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();
        MockHttpSession buyerSession = (MockHttpSession) registered.getRequest().getSession(false);
        assertThat(buyerSession).isNotNull();

        User persisted = users.findByMobileNumber(mobile).orElseThrow();
        assertThat(persisted.getPasswordHash()).isNotBlank();
        assertThat(persisted.getPasswordHash()).isNotEqualTo("buyer-demo-password");
        assertThat(persisted.getSocietyRef().getId()).isEqualTo(societyId);
        assertThat(persisted.getAreaRef().getId()).isEqualTo(areaId);

        mvc.perform(post("/api/auth/register/buyer")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(buyer)))
                .andExpect(status().isConflict());
        assertThat(users.findByMobileNumber(mobile).orElseThrow().getName()).isEqualTo("Buyer " + suffix);

        mvc.perform(get("/api/admin/dashboard").session(buyerSession))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/superadmin/admins").session(buyerSession))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/seller-app/dashboard").session(buyerSession))
                .andExpect(status().isForbidden());

        String secondMobile = uniqueMobile("5");
        Map<String, Object> secondBuyer = Map.of(
                "name", "Second Buyer " + suffix,
                "mobileNumber", secondMobile,
                "password", SECOND_BUYER_TEST_PASSWORD,
                "flatHouseNumber", "C-302",
                "building", "C Wing",
                "areaId", areaId,
                "societyId", societyId
        );
        mvc.perform(post("/api/auth/register/buyer")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(secondBuyer)))
                .andExpect(status().isCreated());
        buyerSession.setAttribute(OrderService.DRAFT_ORDER_SESSION_KEY, "private-draft");
        MvcResult switched = mvc.perform(post("/api/auth/login").session(buyerSession)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", secondMobile, "password", SECOND_BUYER_TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(buyerSession.getAttribute(OrderService.DRAFT_ORDER_SESSION_KEY)).isNull();
        MockHttpSession switchedSession = (MockHttpSession) switched.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(switchedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobileNumber").value(secondMobile));

        mvc.perform(post("/api/auth/logout").with(csrf()).session(buyerSession))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", mobile, "password", WRONG_TEST_PASSWORD))))
                .andExpect(status().isUnauthorized());

        MvcResult loggedIn = mvc.perform(post("/api/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", mobile, "password", BUYER_TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andReturn();
        MockHttpSession loginSession = (MockHttpSession) loggedIn.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(loginSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.mobileNumber").value(mobile));

        mvc.perform(post("/api/auth/demo-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("mobileNumber", mobile))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sellerRegistrationCreatesReservedKitchenAndKeepsApprovalPending() throws Exception {
        String mobile = uniqueMobile("7");
        Map<String, Object> sellerRequest = Map.ofEntries(
                Map.entry("sellerName", "Seller " + suffix),
                Map.entry("mobileNumber", mobile),
                Map.entry("password", SELLER_TEST_PASSWORD),
                Map.entry("whatsappNumber", uniqueMobile("8")),
                Map.entry("alternateContact", uniqueMobile("9")),
                Map.entry("kitchenName", "O'Reilly Kitchen " + suffix),
                Map.entry("kitchenSlug", "oreilly-kitchen-" + suffix),
                Map.entry("speciality", "Maharashtrian breakfast"),
                Map.entry("sellerCategory", "BOTH"),
                Map.entry("shortDescription", "Test seller storefront"),
                Map.entry("instagramLink", "https://instagram.com/demo"),
                Map.entry("primaryAreaId", areaId),
                Map.entry("primarySocietyId", societyId),
                Map.entry("building", "C Wing"),
                Map.entry("serviceAreaId", areaId),
                Map.entry("serviceSocietyIds", List.of(societyId))
        );

        MvcResult registered = mvc.perform(post("/api/auth/register/seller")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(sellerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.sellerApprovalStatus").value("PENDING"))
                .andExpect(jsonPath("$.kitchenSlug").isNotEmpty())
                .andReturn();

        Long sellerId = objectMapper.readTree(registered.getResponse().getContentAsByteArray())
                .get("userId").asLong();
        MockHttpSession sellerSession = (MockHttpSession) registered.getRequest().getSession(false);
        User seller = users.findById(sellerId).orElseThrow();
        var kitchen = kitchens.findBySeller(seller).stream().findFirst().orElseThrow();

        assertThat(seller.getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.PENDING);
        assertThat(seller.getSellerWhatsappNumber()).isEqualTo(sellerRequest.get("whatsappNumber"));
        assertThat(seller.getSellerAlternateContact()).isEqualTo(sellerRequest.get("alternateContact"));
        assertThat(seller.getSellerCategory()).isEqualTo("BOTH");
        assertThat(kitchen.getName()).isEqualTo("oreilly-kitchen-" + suffix);
        assertThat(kitchen.getSpeciality()).isEqualTo("Maharashtrian breakfast");
        assertThat(kitchen.getSellerType().name()).isEqualTo("BOTH");
        assertThat(kitchen.getSociety()).isEqualTo("Direct Auth Society " + suffix);
        assertThat(kitchen.getServedSocieties()).extracting("id").contains(societyId);

        mvc.perform(get("/api/auth/me").session(sellerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerApprovalStatus").value("PENDING"));
        mvc.perform(get("/api/seller-app/dashboard").session(sellerSession))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/seller/coverage-options").session(sellerSession))
                .andExpect(status().isForbidden());

        User admin = users.saveAndFlush(new User("Approval Admin", uniqueMobile("9"), null, UserRole.ADMIN));
        MvcResult adminLogin = mvc.perform(post("/api/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", admin.getMobileNumber(), "password", ADMIN_TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);
        mvc.perform(post("/api/admin/sellers/" + sellerId + "/approve")
                        .with(csrf()).session(adminSession))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(sellerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerApprovalStatus").value("APPROVED"));
        mvc.perform(get("/api/seller/kitchen").session(sellerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(kitchen.getName()));
        mvc.perform(get("/api/kitchens/" + kitchen.getName()))
                .andExpect(status().isOk());
    }

    @Test
    void onlyConfiguredAdminCredentialsCanEnterAdminRoles() throws Exception {
        User admin = users.saveAndFlush(new User("Test Admin", uniqueMobile("9"), null, UserRole.ADMIN));
        User superAdmin = users.saveAndFlush(new User("Test Super Admin", uniqueMobile("8"), null, UserRole.SUPER_ADMIN));

        mvc.perform(post("/api/auth/register/buyer")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "name", "Impersonator", "mobileNumber", admin.getMobileNumber(),
                                "password", BUYER_TEST_PASSWORD,
                                "flatHouseNumber", "A-101", "building", "A Wing",
                                "areaId", areaId, "societyId", societyId))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", admin.getMobileNumber(), "password", WRONG_TEST_PASSWORD))))
                .andExpect(status().isUnauthorized());

        MvcResult adminLogin = mvc.perform(post("/api/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", admin.getMobileNumber(), "password", ADMIN_TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        MockHttpSession adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);
        mvc.perform(get("/api/admin/dashboard").session(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/superadmin/admins").session(adminSession)).andExpect(status().isForbidden());

        MvcResult superLogin = mvc.perform(post("/api/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", superAdmin.getMobileNumber(),
                                "password", SUPER_ADMIN_TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andReturn();
        MockHttpSession superSession = (MockHttpSession) superLogin.getRequest().getSession(false);
        mvc.perform(get("/api/superadmin/admins").session(superSession)).andExpect(status().isOk());
    }

    private static String uniqueMobile(String prefix) {
        return prefix + String.format("%09d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000_000);
    }
}
