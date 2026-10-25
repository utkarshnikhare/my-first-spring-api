package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.dto.*;
import com.example.my_first_spring_api.dto.DiscoveryDtos.KitchenCard;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sociomart-flow1-smoketest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.auth.password-login-enabled=true"
})
@ActiveProfiles("test")
public class SocioMartV1Flow1SmokeTest {

    protected static final String SELLER_PASSWORD = "Password123!";
    protected static final String BUYER_PASSWORD = "Password123!";
    protected static final String ADMIN_PASSWORD = "AdminPassword123!";
    protected static final String SUPER_ADMIN_PASSWORD = "SuperAdminPassword123!";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("sociomart.auth.admin-password", () -> ADMIN_PASSWORD);
        registry.add("sociomart.auth.super-admin-password", () -> SUPER_ADMIN_PASSWORD);
    }

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private LocationService locationService;
    @Autowired private FeatureService featureService;
    @Autowired private SellerService sellerService;
    @Autowired private OrderService orderService;
    @Autowired private AdminService adminService;
    @Autowired private DiscoveryService discoveryService;
    @Autowired private MidnightExpiryJob midnightExpiryJob;
    @Autowired private AdminAuditService adminAuditService;

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        featureService.ensureDefaults();
    }

    @Test
    @DisplayName("SocioMart V1 Flow 1: Full Two-Day Business Smoke Test (Phases 1-7 + Adversarial Checks)")
    void executeFullFlow1TwoDayBusinessSmokeTest() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var area = locationService.createArea("Smoke Area " + suffix);
        var society = locationService.createSociety(area.getId(), "Smoke Society " + suffix);

        // =========================================================================
        // PHASE 1: THREE SELLER REGISTRATIONS & APPLICATION LIFECYCLE
        // =========================================================================
        // 1. Seller 1 (KITCHEN): Asha Cook
        String s1Mobile = "9" + suffix.replaceAll("[^0-9]", "0").substring(0, 4) + "10001";
        String s1Slug = "asha-kitchen-" + suffix;
        Map<String, Object> s1Req = Map.ofEntries(
                Map.entry("sellerName", "Asha Cook " + suffix),
                Map.entry("mobileNumber", s1Mobile),
                Map.entry("password", SELLER_PASSWORD),
                Map.entry("whatsappNumber", "8" + s1Mobile.substring(1)),
                Map.entry("alternateContact", "7" + s1Mobile.substring(1)),
                Map.entry("kitchenName", "Asha Home Kitchen " + suffix),
                Map.entry("kitchenSlug", s1Slug),
                Map.entry("speciality", "North Indian Home Cooking"),
                Map.entry("sellerCategory", "KITCHEN"),
                Map.entry("shortDescription", "Authentic home cooked meals"),
                Map.entry("building", "A Wing"),
                Map.entry("primaryAreaId", area.getId()),
                Map.entry("primarySocietyId", society.getId()),
                Map.entry("serviceAreaId", area.getId()),
                Map.entry("serviceSocietyIds", List.of(society.getId())));
        MvcResult res1 = mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(s1Req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerApprovalStatus").value("PENDING"))
                .andReturn();
        Long seller1Id = json.readTree(res1.getResponse().getContentAsByteArray()).get("userId").asLong();

        // 2. Seller 2 (HOMEMADE_PRODUCTS): Brijesh Pickles & Crafts
        String s2Mobile = "9" + suffix.replaceAll("[^0-9]", "0").substring(0, 4) + "10002";
        String s2Slug = "brijesh-pickles-" + suffix;
        Map<String, Object> s2Req = Map.ofEntries(
                Map.entry("sellerName", "Brijesh Crafts " + suffix),
                Map.entry("mobileNumber", s2Mobile),
                Map.entry("password", SELLER_PASSWORD),
                Map.entry("whatsappNumber", "8" + s2Mobile.substring(1)),
                Map.entry("alternateContact", "7" + s2Mobile.substring(1)),
                Map.entry("kitchenName", "Brijesh Pickles & Crafts " + suffix),
                Map.entry("kitchenSlug", s2Slug),
                Map.entry("speciality", "Traditional Organic Pickles and Spices"),
                Map.entry("sellerCategory", "HOMEMADE_PRODUCTS"),
                Map.entry("shortDescription", "Sun-dried pickles and handcrafted spice blends"),
                Map.entry("building", "B Wing"),
                Map.entry("primaryAreaId", area.getId()),
                Map.entry("primarySocietyId", society.getId()),
                Map.entry("serviceAreaId", area.getId()),
                Map.entry("serviceSocietyIds", List.of(society.getId())));
        MvcResult res2 = mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(s2Req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerApprovalStatus").value("PENDING"))
                .andReturn();
        Long seller2Id = json.readTree(res2.getResponse().getContentAsByteArray()).get("userId").asLong();

        // 3. Seller 3 (BOTH): Chitra Multipurpose
        String s3Mobile = "9" + suffix.replaceAll("[^0-9]", "0").substring(0, 4) + "10003";
        String s3Slug = "chitra-multi-" + suffix;
        Map<String, Object> s3Req = Map.ofEntries(
                Map.entry("sellerName", "Chitra Delight " + suffix),
                Map.entry("mobileNumber", s3Mobile),
                Map.entry("password", SELLER_PASSWORD),
                Map.entry("whatsappNumber", "8" + s3Mobile.substring(1)),
                Map.entry("alternateContact", "7" + s3Mobile.substring(1)),
                Map.entry("kitchenName", "Chitra Multipurpose " + suffix),
                Map.entry("kitchenSlug", s3Slug),
                Map.entry("speciality", "Baking and Home Made Delicacies"),
                Map.entry("sellerCategory", "BOTH"),
                Map.entry("shortDescription", "Fresh home bakes and seasonal sweets"),
                Map.entry("building", "C Wing"),
                Map.entry("primaryAreaId", area.getId()),
                Map.entry("primarySocietyId", society.getId()),
                Map.entry("serviceAreaId", area.getId()),
                Map.entry("serviceSocietyIds", List.of(society.getId())));
        MvcResult res3 = mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(s3Req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerApprovalStatus").value("PENDING"))
                .andReturn();
        Long seller3Id = json.readTree(res3.getResponse().getContentAsByteArray()).get("userId").asLong();

        // Negative Validation: Blank kitchen name rejected
        Map<String, Object> invalidReq = new HashMap<>(s1Req);
        invalidReq.put("kitchenName", "");
        invalidReq.put("mobileNumber", "9" + suffix.replaceAll("[^0-9]", "0").substring(0, 4) + "10099");
        mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(invalidReq)))
                .andExpect(status().isBadRequest());

        // Negative Validation: Duplicate mobile rejected
        mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(s1Req)))
                .andExpect(status().isConflict());

        // Application Lifecycle: Cancellation
        String s4Mobile = "9" + suffix.replaceAll("[^0-9]", "0").substring(0, 4) + "10004";
        Map<String, Object> s4Req = new HashMap<>(s1Req);
        s4Req.put("mobileNumber", s4Mobile);
        s4Req.put("sellerName", "Cancelled Applicant");
        s4Req.put("kitchenSlug", "cancelled-app-" + suffix);
        MvcResult res4 = mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(s4Req)))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession s4Session = (MockHttpSession) res4.getRequest().getSession(false);
        mvc.perform(post("/api/seller/application/cancel").with(csrf()).session(s4Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerApprovalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.sellerStatusReason").value("Application cancelled by applicant"));

        // Application Lifecycle: Changes Requested -> Resubmit
        String s5Mobile = "9" + suffix.replaceAll("[^0-9]", "0").substring(0, 4) + "10005";
        Map<String, Object> s5Req = new HashMap<>(s1Req);
        s5Req.put("mobileNumber", s5Mobile);
        s5Req.put("sellerName", "Resubmitting Applicant");
        s5Req.put("kitchenSlug", "resubmit-app-" + suffix);
        MvcResult res5 = mvc.perform(post("/api/auth/register/seller").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(s5Req)))
                .andExpect(status().isCreated()).andReturn();
        Long s5Id = json.readTree(res5.getResponse().getContentAsByteArray()).get("userId").asLong();
        MockHttpSession s5Session = (MockHttpSession) res5.getRequest().getSession(false);

        // Admin requests changes on S5
        User admin = userRepository.saveAndFlush(new User("Smoke Admin", "6" + suffix.replaceAll("[^0-9]", "1").substring(0, 4) + "20001", null, UserRole.ADMIN));
        MockHttpSession adminSession = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("mobileNumber", admin.getMobileNumber(), "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mvc.perform(post("/api/admin/sellers/" + s5Id + "/request-changes").with(csrf()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("reason", "Please update kitchen display name"))))
                .andExpect(status().isOk());

        User s5User = userRepository.findById(s5Id).orElseThrow();
        assertThat(s5User.getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.CHANGES_REQUESTED);

        // S5 resubmits application with updated details
        mvc.perform(post("/api/seller/application/resubmit").with(csrf()).session(s5Session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("kitchenName", "Updated Kitchen Name " + suffix, "bio", "Updated description"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerApprovalStatus").value("PENDING"));

        s5User = userRepository.findById(s5Id).orElseThrow();
        assertThat(s5User.getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.PENDING);

        // =========================================================================
        // PHASE 2: ADMIN APPROVALS, REJECTIONS & ADVERSARIAL AUTHORIZATION CHECKS
        // =========================================================================
        // Admin approves Seller 1
        mvc.perform(post("/api/admin/sellers/" + seller1Id + "/approve").with(csrf()).session(adminSession))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(seller1Id).orElseThrow().getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.APPROVED);

        // Admin requests changes on Seller 2, Seller 2 resubmits, Admin approves
        mvc.perform(post("/api/admin/sellers/" + seller2Id + "/request-changes").with(csrf()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("reason", "Clarify handmade product types"))))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(seller2Id).orElseThrow().getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.CHANGES_REQUESTED);

        MockHttpSession s2Session = (MockHttpSession) res2.getRequest().getSession(false);
        mvc.perform(post("/api/seller/application/resubmit").with(csrf()).session(s2Session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("kitchenName", "Brijesh Organic Pickles " + suffix))))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(seller2Id).orElseThrow().getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.PENDING);

        mvc.perform(post("/api/admin/sellers/" + seller2Id + "/approve").with(csrf()).session(adminSession))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(seller2Id).orElseThrow().getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.APPROVED);

        // Admin rejects Seller 3
        mvc.perform(post("/api/admin/sellers/" + seller3Id + "/reject").with(csrf()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("reason", "Incomplete verification documents"))))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(seller3Id).orElseThrow().getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.REJECTED);

        // Adversarial check: Rejected Seller 3 cannot access operational seller APIs
        MockHttpSession s3Session = (MockHttpSession) res3.getRequest().getSession(false);
        mvc.perform(get("/api/seller/kitchen").session(s3Session))
                .andExpect(status().isForbidden());

        // =========================================================================
        // PHASE 3: STOREFRONT PUBLISHING (6 OFFERINGS ACROSS 2 DAYS)
        // =========================================================================
        User seller1 = userRepository.findById(seller1Id).orElseThrow();
        Kitchen kitchen1 = kitchenRepository.findBySeller(seller1).stream().findFirst().orElseThrow();
        User seller2 = userRepository.findById(seller2Id).orElseThrow();
        Kitchen kitchen2 = kitchenRepository.findBySeller(seller2).stream().findFirst().orElseThrow();

        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalDate tomorrow = today.plusDays(1);

        // Seller 1 Offering 1: Today's Lunch Thali (Kitchen)
        ProductCreateDto dto1 = new ProductCreateDto();
        dto1.setName("Special Lunch Thali");
        dto1.setDescription("Wholesome meal");
        dto1.setPrice(BigDecimal.valueOf(150));
        dto1.setAvailableDate(today);
        dto1.setCategories(List.of("LUNCH"));
        dto1.setMaxQuantity(10);
        dto1.setOrderWindowStart("06:00");
        dto1.setOrderWindowEnd("23:59");
        dto1.setReadyByTime(today.atTime(13, 0).toString());
        ProductDto p1_lunch = sellerService.createProduct(kitchen1.getId(), dto1, seller1);

        // Seller 1 Offering 2: Today's Dinner Biryani (Kitchen)
        ProductCreateDto dto2 = new ProductCreateDto();
        dto2.setName("Paneer Biryani");
        dto2.setDescription("Fragrant aromatic rice");
        dto2.setPrice(BigDecimal.valueOf(180));
        dto2.setAvailableDate(today);
        dto2.setCategories(List.of("DINNER"));
        dto2.setMaxQuantity(10);
        dto2.setOrderWindowStart("06:00");
        dto2.setOrderWindowEnd("23:59");
        dto2.setReadyByTime(today.atTime(20, 0).toString());
        ProductDto p1_dinner = sellerService.createProduct(kitchen1.getId(), dto2, seller1);

        // Seller 1 Offering 3: Tomorrow's Pre-order Rajma Chawal (Kitchen)
        ProductCreateDto dto3 = new ProductCreateDto();
        dto3.setName("Rajma Chawal Combo");
        dto3.setDescription("Authentic Punjabi recipe");
        dto3.setPrice(BigDecimal.valueOf(130));
        dto3.setAvailableDate(tomorrow);
        dto3.setCategories(List.of("LUNCH"));
        dto3.setMaxQuantity(15);
        dto3.setOrderWindowStart("06:00");
        dto3.setOrderWindowEnd("23:59");
        dto3.setReadyByTime(tomorrow.atTime(13, 0).toString());
        ProductDto p1_preorder = sellerService.createProduct(kitchen1.getId(), dto3, seller1);

        // Seller 2 Offering 4: Today's Mango Pickle (Homemade Product)
        ProductCreateDto dto4 = new ProductCreateDto();
        dto4.setName("Mango Pickle 500g");
        dto4.setDescription("Traditional spicy recipe");
        dto4.setPrice(BigDecimal.valueOf(250));
        dto4.setAvailableDate(today);
        dto4.setCategories(List.of("HOMEMADE"));
        dto4.setMaxQuantity(20);
        dto4.setOrderWindowStart("06:00");
        dto4.setOrderWindowEnd("23:59");
        dto4.setReadyByTime(today.atTime(18, 0).toString());
        ProductDto p2_pickle = sellerService.createProduct(kitchen2.getId(), dto4, seller2);

        // Seller 2 Offering 5: Today's Spice Mix (Homemade Product)
        ProductCreateDto dto5 = new ProductCreateDto();
        dto5.setName("Spice Mix 200g");
        dto5.setDescription("Pure homemade garam masala");
        dto5.setPrice(BigDecimal.valueOf(120));
        dto5.setAvailableDate(today);
        dto5.setCategories(List.of("HOMEMADE"));
        dto5.setMaxQuantity(15);
        dto5.setOrderWindowStart("06:00");
        dto5.setOrderWindowEnd("23:59");
        dto5.setReadyByTime(today.atTime(18, 0).toString());
        ProductDto p2_spices = sellerService.createProduct(kitchen2.getId(), dto5, seller2);

        // Seller 2 Offering 6: Tomorrow's Pre-order Amla Murabba (Homemade Product)
        ProductCreateDto dto6 = new ProductCreateDto();
        dto6.setName("Fresh Amla Murabba 1kg");
        dto6.setDescription("Sweet winter wellness");
        dto6.setPrice(BigDecimal.valueOf(350));
        dto6.setAvailableDate(tomorrow);
        dto6.setCategories(List.of("HOMEMADE"));
        dto6.setMaxQuantity(5);
        dto6.setOrderWindowStart("06:00");
        dto6.setOrderWindowEnd("23:59");
        dto6.setReadyByTime(tomorrow.atTime(18, 0).toString());
        ProductDto p2_murabba = sellerService.createProduct(kitchen2.getId(), dto6, seller2);

        // Negative Validation: Negative price rejected
        ProductCreateDto badPriceDto = new ProductCreateDto();
        badPriceDto.setName("Invalid Price Offering");
        badPriceDto.setPrice(BigDecimal.valueOf(-50));
        badPriceDto.setCategories(List.of("LUNCH"));
        badPriceDto.setAvailableDate(today);
        badPriceDto.setMaxQuantity(5);
        assertThatThrownBy(() -> sellerService.createProduct(kitchen1.getId(), badPriceDto, seller1))
                .isInstanceOf(IllegalArgumentException.class);

        // Verify Storefront Separation:
        // Kitchen storefront returns kitchen products, Homemade storefront returns homemade products
        User testBuyer = new User("Test Buyer", "9100000000", "Flat 10", UserRole.BUYER);
        testBuyer.setSociety(society.getName());
        testBuyer.setArea(area.getName());
        var homemadeStores = discoveryService.getHomemadeStores(testBuyer);
        assertThat(homemadeStores).extracting(KitchenCard::getId).contains(kitchen2.getId());

        // =========================================================================
        // PHASE 4: BUYER ORDERING & PAYMENT STATUS FRAMEWORK
        // =========================================================================
        // Buyer 1: Rohan (orders 2x Lunch Thali from Seller 1, chooses "I've paid")
        String b1Mobile = "9" + suffix.replaceAll("[^0-9]", "2").substring(0, 4) + "30001";
        MvcResult b1Res = mvc.perform(post("/api/auth/register/buyer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "name", "Rohan Sharma",
                                "mobileNumber", b1Mobile,
                                "password", BUYER_PASSWORD,
                                "flatHouseNumber", "101",
                                "building", "Tower A",
                                "areaId", area.getId(),
                                "societyId", society.getId()))))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession buyer1Session = (MockHttpSession) b1Res.getRequest().getSession(false);
        Long buyer1Id = json.readTree(b1Res.getResponse().getContentAsByteArray()).get("userId").asLong();
        User buyer1 = userRepository.findById(buyer1Id).orElseThrow();

        OrderItemRequest item1 = new OrderItemRequest();
        item1.setProductId(p1_lunch.getId());
        item1.setQuantity(2);
        orderService.createOrUpdateDraftOrder(kitchen1.getId(), List.of(item1), buyer1Session);

        PlaceOrderRequest.BuyerDetails bd1 = new PlaceOrderRequest.BuyerDetails();
        bd1.setName(buyer1.getName());
        bd1.setMobileNumber(buyer1.getMobileNumber());
        bd1.setSociety(society.getName());
        bd1.setBuilding("Tower A");
        bd1.setFlatHouseNumber("101");

        OrderDto order1 = orderService.placeOrder(PaymentStatus.PENDING, bd1, "Please pack separately", buyer1Session);
        assertThat(order1.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order1.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order1.getDisplayStatus()).isEqualTo("Confirmed · Buyer says paid");
        assertThat(order1.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));

        // Verify stock decremented: 10 - 2 = 8
        Product p1Db = productRepository.findById(p1_lunch.getId()).orElseThrow();
        assertThat(p1Db.getRemainingQuantity()).isEqualTo(8);

        // Buyer 2: Priya (orders 2x Paneer Biryani from Seller 1, chooses "I'll pay later")
        String b2Mobile = "9" + suffix.replaceAll("[^0-9]", "3").substring(0, 4) + "30002";
        MvcResult b2Res = mvc.perform(post("/api/auth/register/buyer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "name", "Priya Patel",
                                "mobileNumber", b2Mobile,
                                "password", BUYER_PASSWORD,
                                "flatHouseNumber", "202",
                                "building", "Tower B",
                                "areaId", area.getId(),
                                "societyId", society.getId()))))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession buyer2Session = (MockHttpSession) b2Res.getRequest().getSession(false);
        Long buyer2Id = json.readTree(b2Res.getResponse().getContentAsByteArray()).get("userId").asLong();
        User buyer2 = userRepository.findById(buyer2Id).orElseThrow();

        OrderItemRequest item2 = new OrderItemRequest();
        item2.setProductId(p1_dinner.getId());
        item2.setQuantity(2);
        orderService.createOrUpdateDraftOrder(kitchen1.getId(), List.of(item2), buyer2Session);

        PlaceOrderRequest.BuyerDetails bd2 = new PlaceOrderRequest.BuyerDetails();
        bd2.setName(buyer2.getName());
        bd2.setMobileNumber(buyer2.getMobileNumber());
        bd2.setSociety(society.getName());
        bd2.setBuilding("Tower B");
        bd2.setFlatHouseNumber("202");

        OrderDto order2 = orderService.placeOrder(PaymentStatus.WILL_PAY_LATER, bd2, "Less spicy", buyer2Session);
        assertThat(order2.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order2.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order2.getDisplayStatus()).isEqualTo("Confirmed · Buyer says paid");
        assertThat(order2.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(360));

        // Buyer 3: Amit (orders 1x Fresh Amla Murabba from Seller 2, chooses "I've paid")
        String b3Mobile = "9" + suffix.replaceAll("[^0-9]", "4").substring(0, 4) + "30003";
        MvcResult b3Res = mvc.perform(post("/api/auth/register/buyer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "name", "Amit Verma",
                                "mobileNumber", b3Mobile,
                                "password", BUYER_PASSWORD,
                                "flatHouseNumber", "303",
                                "building", "Tower C",
                                "areaId", area.getId(),
                                "societyId", society.getId()))))
                .andExpect(status().isCreated()).andReturn();
        MockHttpSession buyer3Session = (MockHttpSession) b3Res.getRequest().getSession(false);
        Long buyer3Id = json.readTree(b3Res.getResponse().getContentAsByteArray()).get("userId").asLong();
        User buyer3 = userRepository.findById(buyer3Id).orElseThrow();

        OrderItemRequest item3 = new OrderItemRequest();
        item3.setProductId(p2_murabba.getId());
        item3.setQuantity(1);
        orderService.createOrUpdateDraftOrder(kitchen2.getId(), List.of(item3), buyer3Session);

        PlaceOrderRequest.BuyerDetails bd3 = new PlaceOrderRequest.BuyerDetails();
        bd3.setName(buyer3.getName());
        bd3.setMobileNumber(buyer3.getMobileNumber());
        bd3.setSociety(society.getName());
        bd3.setBuilding("Tower C");
        bd3.setFlatHouseNumber("303");

        OrderDto order3 = orderService.placeOrder(PaymentStatus.PENDING, bd3, "Handle with care glass jar", buyer3Session);
        assertThat(order3.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order3.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order3.getDisplayStatus()).isEqualTo("Confirmed · Buyer says paid");

        // Verify stock decremented: 5 - 1 = 4
        Product p2Db = productRepository.findById(p2_murabba.getId()).orElseThrow();
        assertThat(p2Db.getRemainingQuantity()).isEqualTo(4);

        // Adversarial: IDOR Protection - Buyer 1 cannot view Buyer 2's order
        mvc.perform(get("/api/buyer/orders/" + order2.getId()).session(buyer1Session))
                .andExpect(status().isNotFound());

        // Adversarial: Buyer cannot mark their own order as PAID
        mvc.perform(patch("/api/buyer/orders/" + order1.getId() + "/payment-status").with(csrf()).session(buyer1Session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\": \"PAID\"}"))
                .andExpect(status().isBadRequest());

        // =========================================================================
        // PHASE 5: SELLER OPERATIONS & STATE INTEGRITY
        // =========================================================================
        // Seller 1 verifies payment for Order 1:
        // Payment status becomes PAID; order status remains ORDERED (until delivery or completion).
        OrderDto o1Paid = sellerService.markOrderAsPaid(order1.getId(), seller1);
        assertThat(o1Paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(o1Paid.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(o1Paid.getDisplayStatus()).isEqualTo("Confirmed · Paid");

        // Seller 1 delivers Order 1:
        // Order status becomes DELIVERED; payment status remains PAID.
        OrderDto o1Delivered = sellerService.updateOrderStatus(order1.getId(), OrderStatus.DELIVERED, seller1);
        assertThat(o1Delivered.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o1Delivered.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(o1Delivered.getDisplayStatus()).isEqualTo("Delivered · Paid");

        // Adversarial: Seller cannot cancel an already DELIVERED order
        assertThatThrownBy(() -> sellerService.updateOrderStatus(order1.getId(), OrderStatus.CANCELLED, "Try cancel", seller1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already delivered");

        // Seller 1 delivers Order 2 while still unpaid:
        // Order status becomes DELIVERED; payment status remains PENDING.
        OrderDto o2Delivered = sellerService.updateOrderStatus(order2.getId(), OrderStatus.DELIVERED, seller1);
        assertThat(o2Delivered.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o2Delivered.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(o2Delivered.getDisplayStatus()).isEqualTo("Delivered · Payment pending");

        // Seller 1 later marks Order 2's payment received:
        // Order status remains DELIVERED; payment status becomes PAID.
        OrderDto o2Paid = sellerService.markOrderAsPaid(order2.getId(), seller1);
        assertThat(o2Paid.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o2Paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(o2Paid.getDisplayStatus()).isEqualTo("Delivered · Paid");

        // Seller 2 cancels Order 3 with reason:
        // Order status becomes CANCELLED; stock restored from 4 to 5!
        OrderDto o3Cancelled = sellerService.updateOrderStatus(order3.getId(), OrderStatus.CANCELLED, "Customer requested cancellation", seller2);
        assertThat(o3Cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(o3Cancelled.getDisplayStatus()).isEqualTo("Cancelled");

        // Verify stock restoration
        Product p2Restored = productRepository.findById(p2_murabba.getId()).orElseThrow();
        assertThat(p2Restored.getRemainingQuantity()).isEqualTo(5);

        // Adversarial: Cancelled order cannot be cancelled again or modified
        assertThatThrownBy(() -> sellerService.updateOrderStatus(order3.getId(), OrderStatus.CANCELLED, "Again", seller2))
                .isInstanceOf(IllegalArgumentException.class);

        // =========================================================================
        // PHASE 6: ADMIN REPORTING & REVENUE RECONCILIATION
        // =========================================================================
        var seller1Orders = orderService.getSellerOrders(seller1);
        assertThat(seller1Orders).hasSize(2);

        // Verify realized revenue: Order 1 (300) + Order 2 (360) = 660. Cancelled Order 3 is excluded.
        BigDecimal realizedRevenue = seller1Orders.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID)
                .map(SellerOrderSummaryRowDto::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(realizedRevenue).isEqualByComparingTo(BigDecimal.valueOf(660));

        // Verify order persistence in database
        assertThat(orderRepository.findById(order1.getId())).isPresent();
        assertThat(orderRepository.findById(order2.getId())).isPresent();
        assertThat(orderRepository.findById(order3.getId())).isPresent();

        // =========================================================================
        // PHASE 7: NEXT-DAY TRANSITION & MIDNIGHT REFRESH
        // =========================================================================
        // Execute midnight expiry job for tomorrow
        midnightExpiryJob.runForDate(tomorrow);

        // Verify today's past offerings are closed / expired
        var activeCategories = discoveryService.getCategoryTiles(testBuyer);
        assertThat(activeCategories).isNotNull();

        // Verify historical orders remain intact
        Order histOrder1 = orderRepository.findById(order1.getId()).orElseThrow();
        assertThat(histOrder1.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(histOrder1.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(histOrder1.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));

        Order histOrder3 = orderRepository.findById(order3.getId()).orElseThrow();
        assertThat(histOrder3.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Idempotency check: Running midnight expiry again does not corrupt state or crash
        midnightExpiryJob.runForDate(tomorrow);
        assertThat(orderRepository.findById(order1.getId()).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
    }
}
