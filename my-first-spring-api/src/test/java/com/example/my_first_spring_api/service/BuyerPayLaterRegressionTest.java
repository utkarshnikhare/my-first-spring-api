package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.dto.UpdatePaymentStatusRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:buyer-pay-later-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
public class BuyerPayLaterRegressionTest {

    private static final String SOCIETY = "Green Valley Society";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private User seller;
    private Kitchen kitchen;
    private Product product;
    private User buyer;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        orderRepository.deleteAll();
        productRepository.deleteAll();
        kitchenRepository.deleteAll();
        userRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setSociety(SOCIETY);
        seller = userRepository.saveAndFlush(seller);

        kitchen = new Kitchen("k" + suffix, "Sarah's Kitchen " + suffix, "", null, seller);
        kitchen.setSociety(SOCIETY);
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.UPI);
        kitchen.setUpiId("sarah@okaxis");
        kitchen = kitchenRepository.saveAndFlush(kitchen);

        product = new Product(kitchen, "Paneer Butter Masala " + suffix, "desc", new BigDecimal("250.00"), null);
        product.setAvailableToday(true);
        product.setAvailableDate(LocalDate.now());
        product.setRemainingQuantity(20);
        product.setMaxQuantity(10);
        product = productRepository.saveAndFlush(product);

        buyer = new User("Buyer" + suffix, "93" + suffix + "01", "101", UserRole.BUYER);
        buyer.setSociety(SOCIETY);
        buyer.setBuilding("Tower A");
        buyer.setFlatHouseNumber("101");
        buyer = userRepository.saveAndFlush(buyer);
    }

    private MockHttpSession buyerSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        session.setAttribute("user", buyer);
        session.setAttribute("USER_ID", buyer.getId());
        session.setAttribute("ROLE", "BUYER");
        return session;
    }

    private void draft(MockHttpSession session) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(2);
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(item), session);
    }

    @Test
    @DisplayName("Case 1: UPI seller + Buyer chooses 'I've Paid' -> AWAITING_SELLER_VERIFICATION")
    void upiSeller_buyerChoosesIvePaid_statusIsAwaitingSellerVerification() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.UPI);
        kitchen.setUpiId("seller@upi");
        kitchenRepository.saveAndFlush(kitchen);

        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto order = orderService.placeOrder(
                PaymentStatus.AWAITING_SELLER_VERIFICATION, "upi", null, "Please ring bell", session);

        assertThat(order).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order.getPaymentMethod()).isEqualTo("upi");
        assertThat(order.getPaymentConfigurationSnapshot()).isEqualTo("UPI");
    }

    @Test
    @DisplayName("Case 2: UPI seller + Buyer chooses 'I'll Pay Later' -> UNPAID")
    void upiSeller_buyerChoosesPayLater_statusIsUnpaid() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.UPI);
        kitchen.setUpiId("seller@upi");
        kitchenRepository.saveAndFlush(kitchen);

        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto order = orderService.placeOrder(
                PaymentStatus.UNPAID, "upi", null, "Paying on delivery", session);

        assertThat(order).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order.getPaymentMethod()).isEqualTo("upi");
    }

    @Test
    @DisplayName("Case 3: GPay seller + Buyer chooses 'I've Paid' -> AWAITING_SELLER_VERIFICATION")
    void gpaySeller_buyerChoosesIvePaid_statusIsAwaitingSellerVerification() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.GPAY);
        kitchen.setGpayIdentifier("9876543210");
        kitchen.setGpayIdentifierType(GpayIdentifierType.PHONE_NUMBER);
        kitchenRepository.saveAndFlush(kitchen);

        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto order = orderService.placeOrder(
                PaymentStatus.AWAITING_SELLER_VERIFICATION, "gpay", null, null, session);

        assertThat(order).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order.getPaymentMethod()).isEqualTo("gpay");
    }

    @Test
    @DisplayName("Case 4: GPay seller + Buyer chooses 'I'll Pay Later' -> UNPAID")
    void gpaySeller_buyerChoosesPayLater_statusIsUnpaid() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.GPAY);
        kitchen.setGpayIdentifier("seller@gpay");
        kitchen.setGpayIdentifierType(GpayIdentifierType.UPI_ID);
        kitchenRepository.saveAndFlush(kitchen);

        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto order = orderService.placeOrder(
                PaymentStatus.UNPAID, "gpay", null, null, session);

        assertThat(order).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order.getPaymentMethod()).isEqualTo("gpay");
    }

    @Test
    @DisplayName("Case 5: GPAY_AND_UPI seller + Buyer chooses 'I've Paid' -> AWAITING_SELLER_VERIFICATION")
    void gpayAndUpiSeller_buyerChoosesIvePaid_statusIsAwaitingSellerVerification() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.GPAY_AND_UPI);
        kitchen.setUpiId("seller@upi");
        kitchen.setGpayIdentifier("seller@gpay");
        kitchen.setGpayIdentifierType(GpayIdentifierType.UPI_ID);
        kitchenRepository.saveAndFlush(kitchen);

        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto order = orderService.placeOrder(
                PaymentStatus.AWAITING_SELLER_VERIFICATION, "upi", null, null, session);

        assertThat(order).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
    }

    @Test
    @DisplayName("Case 6: GPAY_AND_UPI seller + Buyer chooses 'I'll Pay Later' -> UNPAID")
    void gpayAndUpiSeller_buyerChoosesPayLater_statusIsUnpaid() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.GPAY_AND_UPI);
        kitchen.setUpiId("seller@upi");
        kitchen.setGpayIdentifier("seller@gpay");
        kitchen.setGpayIdentifierType(GpayIdentifierType.UPI_ID);
        kitchenRepository.saveAndFlush(kitchen);

        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto order = orderService.placeOrder(
                PaymentStatus.UNPAID, "gpay", null, null, session);

        assertThat(order).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    @DisplayName("Case 7: Cash Only seller + Buyer chooses 'I'll Pay Later' / COD -> UNPAID")
    void cashOnlySeller_buyerChoosesPayLater_statusIsUnpaid() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.CASH_ONLY);
        kitchenRepository.saveAndFlush(kitchen);

        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto order = orderService.placeOrder(
                PaymentStatus.UNPAID, "cod", null, null, session);

        assertThat(order).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getPaymentMethod()).isEqualTo("cod");
    }

    @Test
    @DisplayName("Case 8: Cash Only seller + Buyer attempts 'I've Paid' -> Rejected with 400")
    void cashOnlySeller_buyerAttemptsAwaitingVerification_isRejected() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.CASH_ONLY);
        kitchenRepository.saveAndFlush(kitchen);

        MockHttpSession session = buyerSession();
        draft(session);

        assertThatThrownBy(() -> orderService.placeOrder(
                PaymentStatus.AWAITING_SELLER_VERIFICATION, "cod", null, null, session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cash-only kitchens do not accept digital payments");
    }

    @Test
    @DisplayName("Case 9: Direct submission of PAID via API is rejected with 400 Bad Request")
    void directSubmissionOfPaid_viaController_isRejectedWithBadRequest() throws Exception {
        MockHttpSession session = buyerSession();
        draft(session);

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setPaymentStatus(PaymentStatus.PAID);
        request.setPaymentMethod("upi");

        mockMvc.perform(post("/api/buyer/orders/place")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Buyers cannot mark an order as paid directly."));
    }

    @Test
    @DisplayName("Case 9b: Direct submission of PAID via patch payment-status is rejected with 400")
    void directSubmissionOfPaid_viaPatch_isRejectedWithBadRequest() throws Exception {
        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto placed = orderService.placeOrder(
                PaymentStatus.UNPAID, "upi", null, null, session);

        UpdatePaymentStatusRequest request = new UpdatePaymentStatusRequest();
        request.setPaymentStatus(PaymentStatus.PAID);

        mockMvc.perform(patch("/api/buyer/orders/" + placed.getId() + "/payment-status")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Buyers cannot mark an order as paid directly."));
    }

    @Test
    @DisplayName("Case 10: Seller verification marks both UNPAID and AWAITING_SELLER_VERIFICATION as PAID")
    void sellerVerificationMarksBothUnpaidAndAwaitingAsPaid() {
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.GPAY_AND_UPI);
        kitchen.setUpiId("seller@upi");
        kitchen.setGpayIdentifier("seller@gpay");
        kitchen.setGpayIdentifierType(GpayIdentifierType.UPI_ID);
        kitchenRepository.saveAndFlush(kitchen);

        // Subcase A: UNPAID order verified by seller
        MockHttpSession sessionA = buyerSession();
        draft(sessionA);
        OrderDto orderA = orderService.placeOrder(
                PaymentStatus.UNPAID, "upi", null, null, sessionA);
        assertThat(orderA.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);

        OrderDto paidA = orderService.markOrderAsPaid(orderA.getId(), seller);
        assertThat(paidA.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Subcase B: AWAITING_SELLER_VERIFICATION order verified by seller
        MockHttpSession sessionB = buyerSession();
        draft(sessionB);
        OrderDto orderB = orderService.placeOrder(
                PaymentStatus.AWAITING_SELLER_VERIFICATION, "gpay", null, null, sessionB);
        assertThat(orderB.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);

        OrderDto paidB = orderService.markOrderAsPaid(orderB.getId(), seller);
        assertThat(paidB.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
