package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.SellerPaymentMode;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 15 Synthetic Kitchens Master Engineering & Concurrency Test Suite.
 *
 * <p>Proves full end-to-end reliability across 15 distinct kitchen profiles:
 * 1. Breakfast (Madras Morning Tiffin)
 * 2. Maharashtrian (Aaji's Maharashtrian Rasoi)
 * 3. North Indian (Punjab Da Dhaba)
 * 4. South Indian (Chettinad Spice Kitchen)
 * 5. Snacks (Sindoori Snacks & Chaat)
 * 6. Bakery (Crust & Crumb Home Bakes)
 * 7. Sweets (Mithai Mahal Homemade)
 * 8. Healthy Meals (Green Fuel NutriBowls)
 * 9. Beverages (Chai & Brew Artisan Cafe)
 * 10. Homemade Specialities (Mom's Special Dabba)
 * 11. Gujarati (Gujarati Rasthal)
 * 12. Bengali (Bengali Swad Kitchen)
 * 13. Rajasthani (Rajasthani Marwar Rasoi)
 * 14. Inactive Kitchen (Sunset Inactive Kitchen - availableToday=false)
 * 15. Pending Approval Seller (Pending Chef Ananya - PENDING approval)
 * </p>
 *
 * <p>Validates catalog discovery, stock reservation, concurrent buyer checkout,
 * financial accuracy, payment transitions, and RBAC defense under an isolated H2 database.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fifteen-kitchens-e2e;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@org.springframework.test.context.ActiveProfiles("test")
public class SyntheticFifteenKitchensIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private LocationService locationService;
    @Autowired private SellerService sellerService;
    @Autowired private OrderService orderService;
    @Autowired private DiscoveryService discoveryService;

    private static final AtomicInteger seq = new AtomicInteger(100);

    private String nextMobile() {
        return "9" + String.format("%08d", seq.incrementAndGet());
    }

    record SyntheticKitchenSpec(
            String name,
            String slug,
            String category,
            String speciality,
            String sellerType,
            boolean activeToday,
            SellerApprovalStatus approvalStatus,
            List<SyntheticProductSpec> products
    ) {}

    record SyntheticProductSpec(
            String name,
            BigDecimal price,
            int stock,
            String category,
            boolean isHomemade
    ) {}

    @Test
    @Transactional
    @DisplayName("15 Synthetic Kitchens: Catalog discovery, state handling, and multi-offering persistence")
    void testFifteenKitchensDiscoveryAndStateHandling() {
        Area area = locationService.createArea("Synthetic Master Area " + nextMobile());
        Society society = locationService.createSociety(area.getId(), "Synthetic Master Society " + nextMobile());

        List<SyntheticKitchenSpec> specs = createFifteenSpecs();
        List<Long> createdKitchenIds = new ArrayList<>();
        List<ProductDto> allCreatedProducts = new ArrayList<>();

        for (SyntheticKitchenSpec spec : specs) {
            User seller = new User("Seller " + spec.name(), nextMobile(), null, UserRole.SELLER);
            seller.setSellerApprovalStatus(spec.approvalStatus());
            seller.setSellerCategory(spec.sellerType());
            seller = userRepository.save(seller);

            KitchenCreateDto kDto = new KitchenCreateDto();
            kDto.setName(spec.slug() + "-" + nextMobile());
            kDto.setDisplayName(spec.name());
            kDto.setDescription(spec.speciality() + " authentic cuisine");
            kDto.setAreaId(area.getId());
            kDto.setSocietyIds(List.of(society.getId()));
            kDto.setAvailableToday(spec.activeToday());
            kDto.setAcceptedPaymentMode(SellerPaymentMode.UPI.name());
            kDto.setUpiId("seller@upi");
            kDto.setSellerType(spec.sellerType());

            KitchenDto kitchen = sellerService.createKitchen(kDto, seller);
            createdKitchenIds.add(kitchen.getId());

            if (spec.approvalStatus() == SellerApprovalStatus.APPROVED) {
                for (SyntheticProductSpec pSpec : spec.products()) {
                    ProductCreateDto pDto = new ProductCreateDto();
                    pDto.setName(pSpec.name());
                    pDto.setPrice(pSpec.price());
                    pDto.setAvailableDate(LocalDate.now());
                    pDto.setAvailableToday(Boolean.TRUE);
                    pDto.setMaxQuantity(pSpec.stock());
                    pDto.setRemainingQuantity(pSpec.stock());
                    pDto.setOrderWindowEnd("23:59");
                    pDto.setReadyByTime(LocalDate.now().plusDays(1).atTime(0, 1).toString());
                    pDto.setCategories(List.of(pSpec.category()));
                    pDto.setIsHomemade(pSpec.isHomemade());

                    ProductDto prod = sellerService.createProduct(kitchen.getId(), pDto, seller);
                    allCreatedProducts.add(prod);
                }
            } else {
                // Pending seller: kitchen is created (slug reserved), but NOT publicly visible to buyers
                Kitchen pendingDbKitchen = kitchenRepository.findById(kitchen.getId()).orElseThrow();
                assertThat(KitchenVisibility.isPubliclyVisible(pendingDbKitchen))
                        .as("Storefront of pending seller must NOT be publicly visible to buyers")
                        .isFalse();
            }
        }

        // All 15 synthetic kitchens created and accounted for
        assertThat(createdKitchenIds).hasSize(15);

        // Discovery service: find kitchens for a resident buyer in this society
        User residentBuyer = new User("Resident Buyer", nextMobile(), "B-101", UserRole.BUYER);
        residentBuyer.setSociety(society.getName());
        residentBuyer.setSocietyRef(society);
        residentBuyer.setAreaRef(area);
        residentBuyer = userRepository.save(residentBuyer);

        var availableKitchens = discoveryService.getKitchens(null, residentBuyer);
        // Exactly 11 active approved hot-meal kitchens returned
        assertThat(availableKitchens).hasSize(11);

        var homemadeStores = discoveryService.getHomemadeStores(residentBuyer);
        // Exactly 2 active approved homemade packaged-goods stores returned
        assertThat(homemadeStores).hasSize(2);

        // Verify products persisted
        assertThat(allCreatedProducts.size()).isGreaterThanOrEqualTo(26);
    }

    @Test
    @DisplayName("Concurrent Checkout across synthetic kitchens: 10 concurrent buyers without overselling")
    void testConcurrentCheckoutAcrossSyntheticKitchens() throws Exception {
        Area area = locationService.createArea("Concurrency Area " + nextMobile());
        Society society = locationService.createSociety(area.getId(), "Concurrency Society " + nextMobile());

        User seller = new User("Master Chef", nextMobile(), null, UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);

        KitchenCreateDto kDto = new KitchenCreateDto();
        kDto.setName("concurrency-kitchen-" + nextMobile());
        kDto.setDisplayName("High Concurrency Kitchen");
        kDto.setDescription("Testing concurrent checkout stress");
        kDto.setAreaId(area.getId());
        kDto.setSocietyIds(List.of(society.getId()));
        kDto.setAvailableToday(true);
        kDto.setAcceptedPaymentMode(SellerPaymentMode.CASH_ONLY.name());
        KitchenDto kitchen = sellerService.createKitchen(kDto, seller);

        // Offering with only 3 units in stock
        ProductCreateDto pDto = new ProductCreateDto();
        pDto.setName("Limited Edition Biryani Thali");
        pDto.setPrice(new BigDecimal("250.00"));
        pDto.setAvailableDate(LocalDate.now());
        pDto.setAvailableToday(Boolean.TRUE);
        pDto.setMaxQuantity(3);
        pDto.setRemainingQuantity(3);
        pDto.setOrderWindowEnd("23:59");
        pDto.setReadyByTime(LocalDate.now().plusDays(1).atTime(0, 1).toString());
        pDto.setCategories(List.of("LUNCH"));
        ProductDto product = sellerService.createProduct(kitchen.getId(), pDto, seller);

        int totalBuyers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalBuyers);
        CountDownLatch readyLatch = new CountDownLatch(totalBuyers);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < totalBuyers; i++) {
            User buyer = new User("Concurrent Buyer " + i, nextMobile(), "Flat " + (100 + i), UserRole.BUYER);
            buyer.setSociety(society.getName());
            buyer.setSocietyRef(society);
            buyer.setAreaRef(area);
            buyer.setBuilding("Tower A");
            buyer = userRepository.save(buyer);

            MockHttpSession session = new MockHttpSession();
            session.setAttribute("BUYER_USER", buyer.getId());

            OrderItemRequest itemReq = new OrderItemRequest();
            itemReq.setProductId(product.getId());
            itemReq.setQuantity(1);
            orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(itemReq), session);

            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                if (!startLatch.await(10, TimeUnit.SECONDS)) {
                    return false;
                }
                try {
                    OrderDto placed = orderService.placeOrder(PaymentStatus.PENDING, "cod", null, null, session);
                    return placed != null && placed.getId() != null;
                } catch (Exception ex) {
                    return false;
                }
            }));
        }

        assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        int successfulOrders = 0;
        int rejectedOrders = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(15, TimeUnit.SECONDS)) {
                successfulOrders++;
            } else {
                rejectedOrders++;
            }
        }
        executor.shutdownNow();

        // Exactly 3 orders must succeed; exactly 7 orders must be safely rejected due to out-of-stock
        assertThat(successfulOrders).as("Only the 3 available items could be ordered").isEqualTo(3);
        assertThat(rejectedOrders).as("The remaining 7 concurrent checkout attempts were cleanly rejected").isEqualTo(7);

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getRemainingQuantity()).isZero();
        assertThat(updatedProduct.getBookedQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Financial Correctness: Item subtotal, order totals, and payment status lifecycle")
    void testFinancialCorrectnessAndLifecycle() {
        Area area = locationService.createArea("Financial Area " + nextMobile());
        Society society = locationService.createSociety(area.getId(), "Financial Society " + nextMobile());

        User seller = new User("Financial Chef", nextMobile(), null, UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = userRepository.save(seller);

        KitchenCreateDto kDto = new KitchenCreateDto();
        kDto.setName("fin-kitchen-" + nextMobile());
        kDto.setDisplayName("Purity Savouries Kitchen");
        kDto.setDescription("Financial test kitchen");
        kDto.setAreaId(area.getId());
        kDto.setSocietyIds(List.of(society.getId()));
        kDto.setAvailableToday(true);
        kDto.setAcceptedPaymentMode(SellerPaymentMode.UPI.name());
        kDto.setUpiId("purity@upi");
        kDto.setSellerType("KITCHEN");
        seller.setSellerCategory("KITCHEN");
        KitchenDto kitchen = sellerService.createKitchen(kDto, seller);

        // Product 1: Veg Cutlet @ 150.00
        ProductCreateDto p1 = new ProductCreateDto();
        p1.setName("Special Veg Cutlet (4 Pcs)");
        p1.setPrice(new BigDecimal("150.00"));
        p1.setAvailableDate(LocalDate.now());
        p1.setAvailableToday(true);
        p1.setMaxQuantity(20);
        p1.setRemainingQuantity(20);
        p1.setOrderWindowEnd("23:59");
        p1.setReadyByTime(LocalDate.now().plusDays(1).atTime(0, 1).toString());
        p1.setCategories(List.of("SNACKS"));
        p1.setIsHomemade(false);
        ProductDto prod1 = sellerService.createProduct(kitchen.getId(), p1, seller);

        // Product 2: Paneer Tikka Roll @ 280.00
        ProductCreateDto p2 = new ProductCreateDto();
        p2.setName("Paneer Tikka Roll Combo");
        p2.setPrice(new BigDecimal("280.00"));
        p2.setAvailableDate(LocalDate.now());
        p2.setAvailableToday(true);
        p2.setMaxQuantity(20);
        p2.setRemainingQuantity(20);
        p2.setOrderWindowEnd("23:59");
        p2.setReadyByTime(LocalDate.now().plusDays(1).atTime(0, 1).toString());
        p2.setCategories(List.of("SNACKS"));
        p2.setIsHomemade(false);
        ProductDto prod2 = sellerService.createProduct(kitchen.getId(), p2, seller);

        User buyer = new User("Fin Buyer", nextMobile(), "101", UserRole.BUYER);
        buyer.setSociety(society.getName());
        buyer.setSocietyRef(society);
        buyer.setAreaRef(area);
        buyer.setBuilding("Tower F");
        buyer = userRepository.save(buyer);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyer.getId());

        // Order 2x Ladoo (300.00) + 3x Katli (840.00) = Expected Total: 1140.00
        OrderItemRequest r1 = new OrderItemRequest();
        r1.setProductId(prod1.getId());
        r1.setQuantity(2);

        OrderItemRequest r2 = new OrderItemRequest();
        r2.setProductId(prod2.getId());
        r2.setQuantity(3);

        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(r1, r2), session);
        OrderDto placed = orderService.placeOrder(PaymentStatus.PENDING, "upi", null, null, session);

        assertThat(placed.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1140.00"));

        Order dbOrder = orderRepository.findByIdWithItems(placed.getId()).orElseThrow();
        assertThat(dbOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1140.00"));
        assertThat(dbOrder.getItems()).hasSize(2);

        // Verify subtotal math for each line item
        dbOrder.getItems().forEach(item -> {
            BigDecimal expectedSubtotal = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            assertThat(item.getSubtotal()).isEqualByComparingTo(expectedSubtotal);
        });

        // Test payment lifecycle: Buyer acknowledges "I've paid" -> AWAITING_SELLER_VERIFICATION
        final User finalBuyer = buyer;
        orderService.updatePaymentStatus(dbOrder.getId(), PaymentStatus.AWAITING_SELLER_VERIFICATION, finalBuyer);
        Order verifyingOrder = orderRepository.findById(dbOrder.getId()).orElseThrow();
        assertThat(verifyingOrder.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);

        // Security assertion: Buyer cannot directly mark as PAID
        assertThatThrownBy(() -> orderService.updatePaymentStatus(dbOrder.getId(), PaymentStatus.PAID, finalBuyer))
                .isInstanceOf(IllegalArgumentException.class);

        // Seller confirms payment -> PAID
        orderService.markOrderAsPaid(dbOrder.getId(), seller);
        Order paidOrder = orderRepository.findById(dbOrder.getId()).orElseThrow();
        assertThat(paidOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    private List<SyntheticKitchenSpec> createFifteenSpecs() {
        return List.of(
                new SyntheticKitchenSpec("Madras Morning Tiffin", "madras-morning-tiffin", "BREAKFAST",
                        "Crisp Ghee Roast Dosas, Steaming Idlis and Fresh Sambar", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Ghee Masala Dosa", new BigDecimal("90.00"), 30, "BREAKFAST", false),
                                new SyntheticProductSpec("Steamed Idli Vada Combo", new BigDecimal("70.00"), 40, "BREAKFAST", false))),

                new SyntheticKitchenSpec("Aaji's Maharashtrian Rasoi", "aajis-maharashtrian-rasoi", "MAHARASHTRIAN",
                        "Authentic Puneri Thalipeeth and Traditional Puran Poli", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Thalipeeth with Makkhan", new BigDecimal("80.00"), 25, "BREAKFAST", false),
                                new SyntheticProductSpec("Puran Poli Platter", new BigDecimal("120.00"), 20, "LUNCH", false))),

                new SyntheticKitchenSpec("Punjab Da Dhaba", "punjab-da-dhaba", "NORTH_INDIAN",
                        "Slow Cooked Dal Makhani and Amritsari Kulcha", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Dal Makhani Bowl", new BigDecimal("160.00"), 25, "LUNCH", false),
                                new SyntheticProductSpec("Amritsari Paneer Kulcha", new BigDecimal("130.00"), 20, "LUNCH", false))),

                new SyntheticKitchenSpec("Chettinad Spice Kitchen", "chettinad-spice-kitchen", "SOUTH_INDIAN",
                        "Rich aromatic pepper gravies and Malabar parottas", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Chettinad Veg Kurma & Parotta", new BigDecimal("140.00"), 20, "DINNER", false),
                                new SyntheticProductSpec("Lemon Rice with Appalam", new BigDecimal("95.00"), 30, "LUNCH", false))),

                new SyntheticKitchenSpec("Sindoori Snacks & Chaat", "sindoori-snacks-chaat", "SNACKS",
                        "Crispy Delhi Samosas and Tangy Bhel Puri", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Punjabi Samosa (2 Pcs)", new BigDecimal("45.00"), 50, "SNACKS", false),
                                new SyntheticProductSpec("Sev Puri Box", new BigDecimal("60.00"), 35, "SNACKS", false))),

                new SyntheticKitchenSpec("Crust & Crumb Home Bakes", "crust-crumb-home-bakes", "BAKERY",
                        "Artisan Sourdough and Decadent Banana Walnut Bread", "HOMEMADE_PRODUCTS", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Country Sourdough Loaf", new BigDecimal("180.00"), 15, "BAKERY", true),
                                new SyntheticProductSpec("Banana Walnut Tea Cake", new BigDecimal("220.00"), 12, "BAKERY", true))),

                new SyntheticKitchenSpec("Mithai Mahal Homemade", "mithai-mahal-homemade", "SWEETS",
                        "Ghee Roasted Besan Ladoo and Hot Gulab Jamun", "HOMEMADE_PRODUCTS", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Shahi Gulab Jamun (4 Pcs)", new BigDecimal("100.00"), 30, "SWEETS", true),
                                new SyntheticProductSpec("Besan Ladoo Box", new BigDecimal("150.00"), 25, "SWEETS", true))),

                new SyntheticKitchenSpec("Green Fuel NutriBowls", "green-fuel-nutribowls", "HEALTHY",
                        "High Protein Quinoa Bowls and Sprout Salads", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Mediterranean Quinoa Salad", new BigDecimal("170.00"), 20, "HEALTHY", false),
                                new SyntheticProductSpec("High Protein Paneer Sprout Bowl", new BigDecimal("140.00"), 25, "HEALTHY", false))),

                new SyntheticKitchenSpec("Chai & Brew Artisan Cafe", "chai-brew-artisan-cafe", "BEVERAGES",
                        "Adrak Elaichi Chai Flask and South Indian Filter Coffee", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Kulhad Masala Chai Flask (500ml)", new BigDecimal("110.00"), 40, "BEVERAGES", false),
                                new SyntheticProductSpec("Traditional Filter Kaapi Flask", new BigDecimal("130.00"), 30, "BEVERAGES", false))),

                new SyntheticKitchenSpec("Mom's Special Dabba", "moms-special-dabba", "HOMEMADE",
                        "Homestyle 4-Compartment Meal with Phulkas", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Daily Comfort Veg Thali", new BigDecimal("140.00"), 35, "LUNCH", false),
                                new SyntheticProductSpec("Special Tawa Pulao with Raita", new BigDecimal("110.00"), 25, "DINNER", false))),

                new SyntheticKitchenSpec("Gujarati Rasthal", "gujarati-rasthal", "GUJARATI",
                        "Soft Methi Theplas and Steamed Nylon Khaman Dhokla", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Methi Thepla Stack (5 Pcs)", new BigDecimal("85.00"), 40, "BREAKFAST", false),
                                new SyntheticProductSpec("Nylon Khaman Dhokla (250g)", new BigDecimal("75.00"), 30, "SNACKS", false))),

                new SyntheticKitchenSpec("Bengali Swad Kitchen", "bengali-swad-kitchen", "BENGALI",
                        "Luchi Alur Dom and Cholar Dal with Coconut", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Luchi with Alur Dom (4 Pcs)", new BigDecimal("115.00"), 25, "BREAKFAST", false),
                                new SyntheticProductSpec("Narkol diye Cholar Dal", new BigDecimal("95.00"), 20, "LUNCH", false))),

                new SyntheticKitchenSpec("Rajasthani Marwar Rasoi", "rajasthani-marwar-rasoi", "RAJASTHANI",
                        "Gatte Ki Sabzi and Traditional Dal Baati Churma", "KITCHEN", true, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Dal Baati Churma Platter", new BigDecimal("190.00"), 20, "LUNCH", false),
                                new SyntheticProductSpec("Govind Gatta Curry", new BigDecimal("135.00"), 25, "DINNER", false))),

                // 14. Inactive Kitchen: Valid approved seller, but kitchen is turned OFF for today
                new SyntheticKitchenSpec("Sunset Inactive Kitchen", "sunset-inactive-kitchen", "SNACKS",
                        "Evening snacks - closed today", "KITCHEN", false, SellerApprovalStatus.APPROVED,
                        List.of(new SyntheticProductSpec("Pakora Platter", new BigDecimal("90.00"), 10, "SNACKS", false))),

                // 15. Pending Approval Kitchen: Seller is awaiting admin approval
                new SyntheticKitchenSpec("Pending Chef Ananya", "pending-chef-ananya", "BAKERY",
                        "Gluten Free Muffins - Application Pending", "HOMEMADE_PRODUCTS", true, SellerApprovalStatus.PENDING,
                        List.of(new SyntheticProductSpec("Gluten Free Choco Muffin", new BigDecimal("120.00"), 10, "BAKERY", true)))
        );
    }
}
