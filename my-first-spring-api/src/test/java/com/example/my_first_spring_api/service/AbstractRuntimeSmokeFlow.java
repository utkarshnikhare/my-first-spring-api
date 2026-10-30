package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.model.DeliveryStatus;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Occurrence;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.RecurringSchedule;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OccurrenceRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.RecurringScheduleRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The marketplace flow shared by the PostgreSQL smoke test (real database,
 * Flyway schema + {@code ddl-auto=validate}) and its H2 twin: seller
 * registration and approval, kitchen + slug lookups, recurring offerings and
 * occurrences, buyer orders, delivery updates and the admin list endpoints.
 */
abstract class AbstractRuntimeSmokeFlow {

    protected static final String SELLER_PASSWORD = newPassword();
    protected static final String BUYER_PASSWORD = newPassword();
    protected static final String ADMIN_PASSWORD = newPassword();
    protected static final String SUPER_ADMIN_PASSWORD = newPassword();

    private static String newPassword() {
        return UUID.randomUUID().toString().replace("-", "") + "Aa1!";
    }

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private RecurringScheduleRepository schedules;
    @Autowired private OccurrenceRepository occurrences;
    @Autowired private ProductRepository products;
    @Autowired private LocationService locations;
    @Autowired private FeatureService featureService;
    @Autowired private SellerService sellerService;
    @Autowired private RecurringScheduleService scheduleService;
    @Autowired private SellerAppService sellerApp;
    @Autowired private KitchenService kitchenService;
    @Autowired private MarketplaceService marketplaceService;
    @Autowired private OrderService orderService;
    @Autowired private SlugService slugService;
    @Autowired private AdminService adminService;
    @Autowired private EnquiryService enquiryService;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void marketplaceFlowRunsEndToEnd() throws Exception {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        featureService.ensureDefaults();
        var area = locations.createArea("Smoke Area " + suffix);
        var society = locations.createSociety(area.getId(), "Smoke Society " + suffix);

        // --- seller registration (HTTP) -> pending, kitchen + slug reserved
        String sellerMobile = "7" + suffix.replaceAll("[^0-9]", "0").substring(0, 4) + "10001";
        String slug = "smoke-kitchen-" + suffix;
        Map<String, Object> sellerRequest = Map.ofEntries(
                Map.entry("sellerName", "Smoke Seller " + suffix),
                Map.entry("mobileNumber", sellerMobile),
                Map.entry("password", SELLER_PASSWORD),
                Map.entry("whatsappNumber", "8" + sellerMobile.substring(1)),
                Map.entry("alternateContact", "9" + sellerMobile.substring(1)),
                Map.entry("kitchenName", "Smoke Kitchen " + suffix),
                Map.entry("kitchenSlug", slug),
                Map.entry("speciality", "Breakfast"),
                Map.entry("sellerCategory", "BOTH"),
                Map.entry("shortDescription", "Postgres smoke test"),
                Map.entry("primaryAreaId", area.getId()),
                Map.entry("primarySocietyId", society.getId()),
                Map.entry("building", "A Wing"),
                Map.entry("serviceAreaId", area.getId()),
                Map.entry("serviceSocietyIds", List.of(society.getId())));
        MvcResult registered = mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(sellerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerApprovalStatus").value("PENDING"))
                .andReturn();
        Long sellerId = json.readTree(registered.getResponse().getContentAsByteArray()).get("userId").asLong();

        // --- admin logs in, sees the pending seller, approves
        User admin = users.saveAndFlush(new User("Smoke Admin", "6" + suffix.replaceAll("[^0-9]", "1").substring(0, 4) + "20001", null, UserRole.ADMIN));
        MockHttpSession adminSession = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("mobileNumber", admin.getMobileNumber(), "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        assertThat(adminService.pendingSellers()).extracting(User::getId).contains(sellerId);
        mvc.perform(post("/api/admin/sellers/" + sellerId + "/approve").with(csrf()).session(adminSession))
                .andExpect(status().isOk());

        User seller = users.findById(sellerId).orElseThrow();
        Kitchen kitchen = kitchens.findBySeller(seller).stream().findFirst().orElseThrow();
        assertThat(kitchen.getSlug()).isEqualTo(slug);

        // --- recurring offering for today + its occurrence
        LocalDate today = LocalDate.now();
        ProductCreateDto dto = new ProductCreateDto();
        dto.setName("Smoke Poha");
        dto.setPrice(BigDecimal.valueOf(40));
        dto.setCategories(List.of("BREAKFAST"));
        dto.setAvailableDate(today);
        dto.setMaxQuantity(20);
        dto.setOrderWindowEnd("23:58");
        dto.setReadyByTime(today.atTime(23, 59).toString());
        ProductDto product = sellerService.createProduct(kitchen.getId(), dto, seller);
        scheduleService.createSchedule(
                products.findById(product.getId()).orElseThrow(),
                today, today.plusDays(14), EnumSet.of(today.getDayOfWeek()),
                10, "23:58", today.atTime(23, 59).toString());
        RecurringSchedule schedule = schedules.findByProductId(product.getId()).orElseThrow();
        Occurrence occurrence = occurrences.findByScheduleIdAndOccurrenceDate(schedule.getId(), today).orElseThrow();
        assertThat(sellerApp.getDashboard(seller).getOfferings()).extracting(ProductDto::getId).contains(product.getId());

        // --- buyer registers (HTTP), browses and orders
        String buyerMobile = "5" + suffix.replaceAll("[^0-9]", "2").substring(0, 4) + "30001";
        Map<String, Object> buyerRequest = Map.of(
                "name", "Smoke Buyer " + suffix, "mobileNumber", buyerMobile, "password", BUYER_PASSWORD,
                "flatHouseNumber", "B-1", "building", "B Wing",
                "areaId", area.getId(), "societyId", society.getId());
        mvc.perform(post("/api/auth/register/buyer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(buyerRequest)))
                .andExpect(status().isCreated());
        User buyer = users.findByMobileNumber(buyerMobile).orElseThrow();
        KitchenDetailDto detail = kitchenService.getKitchenDetailById(kitchen.getId(), buyer);
        assertThat(detail.getProducts()).extracting(ProductDto::getId).contains(product.getId());
        assertThat(marketplaceService.getAllActiveKitchens(buyer)).isNotEmpty();
        assertThat(marketplaceService.getAllAvailableItems(buyer)).extracting(ProductDto::getId).contains(product.getId());

        MockHttpSession buyerSession = new MockHttpSession();
        buyerSession.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(2);
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(item), buyerSession);
        OrderDto placed = orderService.placeOrder(PaymentStatus.PENDING, null, null, null, buyerSession);
        assertThat(placed.getItems()).hasSize(1);
        assertThat(placed.getItems().get(0).getOccurrenceId()).isEqualTo(occurrence.getId());
        assertThat(occurrences.findById(occurrence.getId())).isPresent();

        // --- seller advances delivery, buyer enquires
        orderService.updateDeliveryStatus(placed.getId(), DeliveryStatus.DELIVERED, seller);
        assertThat(enquiryService.submit(buyer, kitchen.getId(), "Can you cater for 10?", null, "10", null)).isNotNull();

        // --- slug lookups: public routes, rename -> permanent alias redirect
        mvc.perform(get("/api/kitchens/by-slug/" + slug)).andExpect(status().isOk());
        mvc.perform(get("/api/kitchens/slug-check").param("slug", slug))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/kitchens/slug-suggestion").param("name", "Smoke Kitchen " + suffix))
                .andExpect(status().isOk());
        mvc.perform(get("/kitchens")).andExpect(status().isOk());
        mvc.perform(get("/kitchen/" + slug)).andExpect(status().isOk());
        String renamed = slugService.changeSlug(kitchens.findById(kitchen.getId()).orElseThrow(), slug + "-new");
        assertThat(slugService.resolveBySlug(slug)).get().extracting(Kitchen::getId).isEqualTo(kitchen.getId());
        mvc.perform(get("/kitchen/" + slug)).andExpect(redirectedUrl("/" + "kitchen/" + renamed));
        assertThat(kitchens.findBySlug(renamed)).isPresent();

        // --- every admin list / dashboard query
        assertThat(adminService.dashboard()).isNotNull();
        assertThat(adminService.buyers()).isNotEmpty();
        assertThat(adminService.kitchens()).isNotEmpty();
        assertThat(adminService.offerings()).isNotEmpty();
        assertThat(adminService.enquiries()).isNotEmpty();
        assertThat(adminService.attentionItems()).isNotNull();
        assertThat(adminService.societies()).isNotNull();
        assertThat(adminService.coverageOptions()).isNotEmpty();
        assertThat(adminService.retentionPreview()).isNotNull();
        assertThat(adminService.retention()).isNotNull();
        assertThat(adminService.marketplaceLinks()).isNotNull();
        assertThat(adminService.analyticsSummary()).isNotNull();
        assertThat(adminService.locations()).isNotNull();
        assertThat(adminService.systemHealth()).isNotNull();
        mvc.perform(get("/api/admin/dashboard").session(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/kitchens").session(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
