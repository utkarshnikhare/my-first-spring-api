package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end demo marketplace flow over REAL persistence.
 *
 * <p>No service is mocked: every step runs through the production services
 * ({@link SellerService}, {@link OrderService}, {@link LocationService},
 * {@link DiscoveryService}) against a real H2 database created by JPA, so the
 * assertions describe what the application actually persists.</p>
 *
 * <p>The flow creates a BRAND-NEW Area, Society, seller, kitchen and products rather
 * than relying on any seeded demo record, so it proves real marketplace behaviour
 * rather than a static demo.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-e2e;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@org.springframework.test.context.ActiveProfiles("test")
class DemoMarketplaceE2ETest {

    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private LocationService locationService;
    @Autowired private SellerService sellerService;
    @Autowired private OrderService orderService;
    @Autowired private DiscoveryService discoveryService;
    @Autowired private KitchenService kitchenService;

    /** Static so every test in this class gets a unique mobile (the column is UNIQUE). */
    private static int mobileSeq = 0;

    private static synchronized String nextMobile() {
        return "9" + String.format("%08d", 10000000 + (++mobileSeq));
    }

    /** An approved seller, which is what makes a kitchen publicly visible. */
    private User approvedSeller(String tag) {
        User seller = new User("Seller " + tag, nextMobile(), null, UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return userRepository.save(seller);
    }

    /** A buyer with a COMPLETE profile and an ID-backed society reference. */
    private User completeBuyer(String tag, Society society) {
        User buyer = new User("Buyer " + tag, nextMobile(), "A-101", UserRole.BUYER);
        buyer.setSociety(society.getName());
        buyer.setSocietyRef(society);
        buyer.setAreaRef(society.getArea());
        buyer.setBuilding("B-1");
        return userRepository.save(buyer);
    }

    /** A brand-new kitchen created through the real seller service. */
    private KitchenDto newKitchen(User seller, Area area, Society society) {
        KitchenCreateDto dto = new KitchenCreateDto();
        dto.setName("e2e-kitchen-" + nextMobile());
        dto.setDisplayName("E2E Tiffin Kitchen " + nextMobile());
        dto.setDescription("Created by the end-to-end test");
        dto.setAreaId(area.getId());
        dto.setSocietyIds(List.of(society.getId()));
        dto.setAvailableToday(Boolean.TRUE);
        return sellerService.createKitchen(dto, seller);
    }

    /**
     * A brand-new offering with a known stock level.
     *
     * <p>The order window is derived from the REAL current clock rather than hardcoded,
     * because the backend correctly refuses an order once today's cutoff has passed
     * ("Orders Close must not already have passed"). Close = now + 2h, delivery =
     * now + 4h, which keeps the offering genuinely orderable at the moment it is created.</p>
     */
    private ProductDto newProduct(User seller, Long kitchenId, String name, int stock) {
        LocalDateTime now = LocalDateTime.now();
        int nowMin = now.getHour() * 60 + now.getMinute();
        // The close window is now + 2h, capped at 22:00 so the offering stays
        // orderable. When the cap is reached exactly at the current minute (or
        // we are past 22:00), the offering cannot be created for today; move it
        // to tomorrow so the window stays open at creation time.
        LocalDate offeringDate = LocalDate.now();
        int closeMin = Math.min(nowMin + 120, 22 * 60);
        if (closeMin <= nowMin) {
            offeringDate = LocalDate.now().plusDays(1);
            closeMin = 22 * 60;
        }
        int readyMin = Math.min(nowMin + 240, 23 * 60);
        ProductCreateDto dto = new ProductCreateDto();
        dto.setName(name);
        dto.setPrice(new BigDecimal("120.00"));
        dto.setAvailableDate(offeringDate);
        dto.setAvailableToday(Boolean.TRUE);
        dto.setMaxQuantity(stock);
        dto.setRemainingQuantity(stock);
        dto.setOrderWindowEnd(String.format("%02d:%02d", closeMin / 60, closeMin % 60));
        // The ready time must land on/after the offering date. For today's
        // offerings the legacy HH:mm format is anchored to today, so it is fine;
        // for tomorrow's offerings we pass a full date-time so the ready date
        // lands on the offering date instead of today.
        String readyBy = String.format("%02d:%02d", readyMin / 60, readyMin % 60);
        if (offeringDate.isAfter(LocalDate.now())) {
            readyBy = offeringDate.atTime(readyMin / 60, readyMin % 60).toString();
        }
        dto.setReadyByTime(readyBy);
        dto.setCategories(List.of("LUNCH"));
        return sellerService.createProduct(kitchenId, dto, seller);
    }

    /** Draft then place, exactly as the buyer app does. */
    private OrderDto order(MockHttpSession session, Long kitchenId, List<OrderItemRequest> items) {
        orderService.createOrUpdateDraftOrder(kitchenId, items, session);
        return orderService.placeOrder(PaymentStatus.PENDING, null, null, session);
    }

    private static OrderItemRequest item(Long productId, int qty) {
        OrderItemRequest r = new OrderItemRequest();
        r.setProductId(productId);
        r.setQuantity(qty);
        return r;
    }

    private void buyerSession(MockHttpSession session, User buyer) {
        session.setAttribute("BUYER_USER", buyer.getId());
    }
// =====================================================================
    // THE ACCEPTANCE FLOW - every record below is created inside the test.
    // =====================================================================

    @Test
    @DisplayName("E2E: new kitchen -> new items -> buyer discovers -> orders -> stock moves -> both see it")
    void fullMarketplaceEndToEnd() {
        // ---- 1. NEW master data (not seeded) ----
        Area area = locationService.createArea("E2E Area " + nextMobile());
        Society society = locationService.createSociety(area.getId(), "E2E Society " + nextMobile());

        // ---- 2. NEW approved seller ----
        User seller = approvedSeller("primary");

        // ---- 3. NEW kitchen, persisted, with explicit Society-ID coverage ----
        KitchenDto kitchenDto = newKitchen(seller, area, society);
        Long kitchenId = kitchenDto.getId();
        assertThat(kitchenId).as("new kitchen persisted with an id").isNotNull();

        Kitchen kitchen = kitchenRepository.findById(kitchenId).orElseThrow();
        assertThat(kitchen.getDisplayName()).isEqualTo(kitchenDto.getDisplayName());
        assertThat(kitchen.getSeller().getId()).as("kitchen is owned by the new seller")
                .isEqualTo(seller.getId());
        assertThat(kitchen.getServedSocieties()).as("coverage is the explicit Society ID")
                .extracting(Society::getId).containsExactly(society.getId());

        // ---- 4. NEW items with known stock ----
        ProductDto itemA = newProduct(seller, kitchenId, "E2E Paneer Thali", 10);
        ProductDto itemB = newProduct(seller, kitchenId, "E2E Dal Rice", 5);
        assertThat(itemA.getId()).isNotNull();
        assertThat(itemB.getId()).isNotNull();
        assertThat(productRepository.findById(itemA.getId()).orElseThrow().getRemainingQuantity())
                .as("item A initial stock persisted").isEqualTo(10);
        assertThat(productRepository.findById(itemB.getId()).orElseThrow().getRemainingQuantity())
                .as("item B initial stock persisted").isEqualTo(5);
        assertThat(productRepository.findById(itemA.getId()).orElseThrow().getKitchen().getId())
                .as("item belongs to the new kitchen").isEqualTo(kitchenId);

        // ---- 5. NEW buyer in the same society ----
        User buyer = completeBuyer("primary", society);
        MockHttpSession session = new MockHttpSession();
        buyerSession(session, buyer);

        // ---- 6. Buyer DISCOVERS the new kitchen through the existing visibility engine ----
        var cards = discoveryService.getKitchens("ALL", buyer);
        assertThat(cards).as("new kitchen is discoverable by an eligible buyer")
                .anySatisfy(c -> assertThat(c.getDisplayName()).contains(kitchen.getDisplayName()));

        // ---- 7. Buyer sees the NEW items of that kitchen ----
        // getProductsByKitchenName resolves by Kitchen.name (the URL slug), not the
        // display label - the name-based lookup the audit flagged as F-02.
        var itemsForKitchen = kitchenService.getProductsByKitchenName(kitchen.getName(), buyer);
        assertThat(itemsForKitchen).extracting(ProductDto::getName)
                .contains("E2E Paneer Thali", "E2E Dal Rice");

        // ---- 8. Buyer places an order: A=2, B=1 ----
        OrderDto placed = order(session, kitchenId, List.of(
                item(itemA.getId(), 2), item(itemB.getId(), 1)));

        assertThat(placed.getId()).as("order persisted with an id").isNotNull();
        assertThat(placed.getOrderNumber()).isNotBlank();
        assertThat(placed.getItems()).as("both line items persisted")
                .hasSize(2);

        // ---- 9. Quantities on the order are exactly what was requested ----
        Order saved = orderRepository.findByIdWithItems(placed.getId()).orElseThrow();
        assertThat(saved.getItems()).hasSize(2);
        int qtyA = 0, qtyB = 0;
        for (OrderItem oi : saved.getItems()) {
            String n = oi.getProduct().getName();
            if ("E2E Paneer Thali".equals(n)) qtyA = oi.getQuantity();
            if ("E2E Dal Rice".equals(n)) qtyB = oi.getQuantity();
        }
        assertThat(qtyA).as("item A ordered quantity").isEqualTo(2);
        assertThat(qtyB).as("item B ordered quantity").isEqualTo(1);
        assertThat(saved.getTotalAmount()).as("total = 2*120 + 1*120")
                .isEqualByComparingTo(new BigDecimal("360.00"));

        // ---- 10. STOCK correctly deducted from the database ----
        assertThat(productRepository.findById(itemA.getId()).orElseThrow().getRemainingQuantity())
                .as("item A stock 10 - 2 = 8").isEqualTo(8);
        assertThat(productRepository.findById(itemB.getId()).orElseThrow().getRemainingQuantity())
                .as("item B stock 5 - 1 = 4").isEqualTo(4);

        // ---- 11. Seller sees the order ----
        var sellerOrders = sellerService.getMyOrders(seller);
        assertThat(sellerOrders).as("seller sees the new order")
                .anySatisfy(row -> assertThat(row.getOrderNumber()).isEqualTo(placed.getOrderNumber()));

        // ---- 12. Buyer sees the order (getMyOrders groups by status bucket) ----
        var myOrders = orderService.getMyOrders(buyer);
        List<String> buyerOrderNumbers = myOrders.values().stream()
                .flatMap(List::stream)
                .map(OrderDto::getOrderNumber)
                .toList();
        assertThat(buyerOrderNumbers).as("buyer sees the new order in their own order list")
                .contains(placed.getOrderNumber());

        // ---- 13. Time-based availability: offering date is a real, valid date ----
        assertThat(itemA.getAvailableDate()).as("availability uses a real date, not a hardcoded one")
                .isNotNull();
    }
// =====================================================================
    // INVENTORY CORRECTNESS - proving the EXISTING stock implementation.
    // No new locking is introduced here; these tests exercise what is already there.
    // =====================================================================

    @Test
    @DisplayName("Inventory: valid qty deducts, invalid qty rejected, stock unchanged after rejection")
    void inventoryQuantityCorrectness() {
        Area area = locationService.createArea("Inv Area " + nextMobile());
        Society society = locationService.createSociety(area.getId(), "Inv Society " + nextMobile());
        User seller = approvedSeller("inv");
        KitchenDto k = newKitchen(seller, area, society);
        ProductDto stock10 = newProduct(seller, k.getId(), "Stock Ten", 10);
        ProductDto stock5 = newProduct(seller, k.getId(), "Stock Five", 5);

        // --- zero and negative are rejected, stock untouched ---
        MockHttpSession s1 = new MockHttpSession();
        buyerSession(s1, completeBuyer("zero", society));
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(k.getId(),
                List.of(item(stock10.getId(), 0)), s1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(k.getId(),
                List.of(item(stock10.getId(), -3)), s1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(productRepository.findById(stock10.getId()).orElseThrow().getRemainingQuantity())
                .as("rejected quantities must not change stock").isEqualTo(10);

        // --- more than stock is rejected, stock untouched ---
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(k.getId(),
                List.of(item(stock10.getId(), 11)), s1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(productRepository.findById(stock10.getId()).orElseThrow().getRemainingQuantity())
                .isEqualTo(10);

        // --- a valid multi-item order deducts each line correctly ---
        MockHttpSession s2 = new MockHttpSession();
        buyerSession(s2, completeBuyer("valid", society));
        OrderDto placed = order(s2, k.getId(), List.of(
                item(stock10.getId(), 3), item(stock5.getId(), 2)));

        assertThat(productRepository.findById(stock10.getId()).orElseThrow().getRemainingQuantity())
                .as("10 - 3 = 7").isEqualTo(7);
        assertThat(productRepository.findById(stock5.getId()).orElseThrow().getRemainingQuantity())
                .as("5 - 2 = 3").isEqualTo(3);
        assertThat(placed.getTotalAmount()).as("3*120 + 2*120 = 600")
                .isEqualByComparingTo(new BigDecimal("600.00"));
    }

    @Test
    @DisplayName("Authorization: a buyer cannot create products, and cannot order from another society")
    void authorizationBoundaries() {
        Area area = locationService.createArea("Authz Area " + nextMobile());
        Society society = locationService.createSociety(area.getId(), "Authz Society " + nextMobile());
        Area otherArea = locationService.createArea("Other Area " + nextMobile());
        Society otherSociety = locationService.createSociety(otherArea.getId(), "Other Society " + nextMobile());

        User seller = approvedSeller("authz");
        KitchenDto k = newKitchen(seller, area, society);
        ProductDto p = newProduct(seller, k.getId(), "Guarded Item", 5);

        // A BUYER may not create products for someone else's kitchen.
        User buyer = completeBuyer("authz", society);
        ProductCreateDto forged = new ProductCreateDto();
        forged.setName("Forged");
        forged.setPrice(new BigDecimal("10.00"));
        forged.setAvailableDate(LocalDate.now());
        forged.setOrderWindowEnd("22:00");
        forged.setReadyByTime("23:00");
        forged.setCategories(List.of("LUNCH"));
        assertThatThrownBy(() -> sellerService.createProduct(k.getId(), forged, buyer))
                .as("a buyer cannot add offerings to a kitchen")
                .isInstanceOf(RuntimeException.class);

        // A buyer in a DIFFERENT society must not see or order from this kitchen.
        User outsider = completeBuyer("outsider", otherSociety);
        MockHttpSession s = new MockHttpSession();
        buyerSession(s, outsider);
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(k.getId(),
                List.of(item(p.getId(), 1)), s))
                .as("a buyer outside the covered society cannot order")
                .isInstanceOf(RuntimeException.class);
        assertThat(productRepository.findById(p.getId()).orElseThrow().getRemainingQuantity())
                .as("blocked order leaves stock untouched").isEqualTo(5);
    }
}
