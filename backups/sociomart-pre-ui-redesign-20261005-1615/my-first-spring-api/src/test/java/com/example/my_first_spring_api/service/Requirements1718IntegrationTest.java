package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:requirements-17-18;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class Requirements1718IntegrationTest {
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired SellerAppService sellerApp;
    @Autowired KitchenService kitchenService;
    @Autowired OrderService orderService;

    private User seller;
    private User buyer;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.saveAndFlush(seller);
        buyer = new User("Buyer" + suffix, "93" + suffix + "01", "A-1", UserRole.BUYER);
        buyer.setSociety("Society");
        buyer.setBuilding("A");
        buyer = users.saveAndFlush(buyer);
        kitchen = kitchens.saveAndFlush(new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller));
    }


    @Test
    void historyOnlySellerHasNoCurrentOfferingsAndNoEarnings() {
        products.saveAndFlush(product("Yesterday", LocalDate.now().minusDays(1), false));

        var dashboard = sellerApp.getDashboard(seller);
        assertThat(dashboard.getOfferings()).isEmpty();
        assertThat(dashboard.getTotalOrders()).isZero();
        assertThat(dashboard.hasEarnings()).isFalse();
        assertThat(sellerApp.getEarnings(seller).hasEarnings()).isFalse();
        assertThat(sellerApp.getRecentOfferings(seller))
                .extracting(com.example.my_first_spring_api.dto.ProductDto::getName)
                .containsExactly("Yesterday");
    }

    @Test
    void currentOfferingStaysVisibleButPendingOnlyMeansNoEarnings() {
        Product current = products.saveAndFlush(product("Today", LocalDate.now(), false));
        order(current, OrderStatus.CONFIRMED, PaymentStatus.PENDING);

        var dashboard = sellerApp.getDashboard(seller);
        assertThat(dashboard.getOfferings()).extracting(com.example.my_first_spring_api.dto.ProductDto::getName)
                .containsExactly("Today");
        assertThat(dashboard.hasEarnings()).isFalse();
        var earnings = sellerApp.getEarnings(seller);
        assertThat(earnings.hasEarnings()).isFalse();
        assertThat(earnings.getPending()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void paidNonCancelledOrderMeansEarningsExist() {
        Product current = products.saveAndFlush(product("Paid item", LocalDate.now(), false));
        order(current, OrderStatus.CONFIRMED, PaymentStatus.PAID);
        assertThat(sellerApp.getDashboard(seller).hasEarnings()).isTrue();
        assertThat(sellerApp.getEarnings(seller).hasEarnings()).isTrue();
    }

    @Test
    void afterCloseDtoIsClosedAndDirectDraftOrderCannotDecrementInventory() {
        String close = LocalTime.now().minusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm"));
        Product expired = product("Closed item", LocalDate.now(), false);
        expired.setOrderWindowEnd(close);
        expired.setCutoffTime(close);
        final Product persistedExpired = products.saveAndFlush(expired);

        KitchenDetailDto detail = kitchenService.getKitchenDetailById(kitchen.getId(), buyer);
        var dto = detail.getProducts().stream().filter(p -> p.getId().equals(persistedExpired.getId()))
                .findFirst().orElseThrow();
        assertThat(dto.getOrdersClosed()).isTrue();
        assertThat(dto.getLifecycleState()).isEqualTo("ORDERS_CLOSED");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(persistedExpired.getId());
        request.setQuantity(1);
        long ordersBefore = orders.count();
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request), session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orders today are closed");
        assertThat(products.findById(persistedExpired.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(3);
        assertThat(orders.count()).isEqualTo(ordersBefore);
    }

    @Test
    void staleDraftIsRejectedAtFinalPlacementAfterCutoffWithoutDecrementingInventory() {
        Product product = products.saveAndFlush(product("Stale item", LocalDate.now(), false));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(product.getId());
        request.setQuantity(1);
        var draft = orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request), session);
        String close = LocalTime.now().minusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm"));
        product.setOrderWindowEnd(close);
        product.setCutoffTime(close);
        products.saveAndFlush(product);

        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PENDING, null, null, session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orders today are closed");
        assertThat(orders.findById(draft.getId()).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    void sellerUiUsesRequiredEmptyStateCopyAndExistingCreateRoute() throws Exception {
        String sellerJs;
        try (var in = new ClassPathResource("static/js/seller.js").getInputStream()) {
            sellerJs = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sellerJs).contains(
                "No Offerings",
                "Nothing on sale right now. Create your first offering and start taking orders.",
                "+ Create Offering",
                "href=\"#/create\"",
                "No Orders",
                "No orders yet. New orders will appear here.",
                "No Earnings",
                "No earnings to show yet");
    }



    private Product product(String name, LocalDate date, boolean preorder) {
        Product p = new Product(kitchen, name, "", BigDecimal.valueOf(40), null);
        p.setAvailableDate(date);
        p.setAvailableToday(date.equals(LocalDate.now()) && !preorder);
        p.setIsPreorder(preorder);
        p.setMaxQuantity(3);
        p.setRemainingQuantity(3);
        p.setOrderWindowStart(null);
        p.setOrderWindowEnd("23:58");
        p.setCutoffTime("23:58");
        p.setReadyByTime(date + "T23:59");
        return p;
    }

    private void order(Product product, OrderStatus status, PaymentStatus payment) {
        Order order = new Order(buyer, kitchen);
        order.setOrderNumber("R17-18-" + UUID.randomUUID());
        order.setOrderStatus(status);
        order.setPaymentStatus(payment);
        order.addItem(new OrderItem(product, 1, product.getPrice()));
        order.recalculateTotal();
        orders.saveAndFlush(order);
    }
}

