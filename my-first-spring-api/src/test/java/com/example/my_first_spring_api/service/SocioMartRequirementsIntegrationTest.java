package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemDetailDto;
import com.example.my_first_spring_api.dto.SellerOrderSummaryDto;
import com.example.my_first_spring_api.dto.SellerOrderSummaryRowDto;
import com.example.my_first_spring_api.exception.OrderNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:requirements-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SocioMartRequirementsIntegrationTest {
    @Autowired OrderService orders;
    @Autowired SellerService sellerService;
    @Autowired SellerAppService sellerApp;
    @Autowired AdminService admin;
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orderRepo;
    @Autowired PlatformTransactionManager tx;

    private Long orderId;
    private Long sellerId;
    private Long buyerId;
    private Long otherId;
    private Long productId;
    private User seller;
    private User buyer;
    private User other;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        Long[] ids = new TransactionTemplate(tx).execute(status -> {
            User sellerEntity = users.save(new User("Seller" + suffix, "91" + suffix + "0001", null, UserRole.SELLER));
            sellerEntity.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
            User otherEntity = users.save(new User("Other" + suffix, "92" + suffix + "0001", null, UserRole.SELLER));
            User buyerEntity = users.save(new User("Buyer" + suffix, "93" + suffix + "0001", "A-1", UserRole.BUYER));
            buyerEntity.setSociety("Society");
            buyerEntity.setBuilding("A");

            Kitchen kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, sellerEntity);
            kitchen.setAcceptedPaymentMode(SellerPaymentMode.GPAY);
            kitchen.setGpayIdentifierType(GpayIdentifierType.PHONE_NUMBER);
            kitchen.setGpayIdentifier("9876543210");
            kitchen = kitchens.save(kitchen);

            Product product = new Product(kitchen, "Poha", "", BigDecimal.valueOf(25), null);
            product.setAvailableToday(true);
            product.setRemainingQuantity(50);
            product = products.save(product);

            Order order = new Order(buyerEntity, kitchen);
            order.setOrderNumber("SM" + suffix);
            order.addItem(new OrderItem(product, 2, BigDecimal.valueOf(25)));
            order.recalculateTotal();
            orderRepo.save(order);
            return new Long[]{order.getId(), sellerEntity.getId(), buyerEntity.getId(),
                    otherEntity.getId(), product.getId()};
        });

        orderId = ids[0];
        sellerId = ids[1];
        buyerId = ids[2];
        otherId = ids[3];
        productId = ids[4];
        seller = users.findById(sellerId).orElseThrow();
        buyer = users.findById(buyerId).orElseThrow();
        other = users.findById(otherId).orElseThrow();
    }

    @Test
    void phoneSummaryDetailAndOwnership() throws Exception {
        SellerOrderSummaryDto summary = sellerApp.getOrderSummary(seller, LocalDate.now());
        OrderItemDetailDto drill = sellerApp.getOrderItemDetail(seller, productId, LocalDate.now(), null, null, null);
        List<SellerOrderSummaryRowDto> list = sellerService.getMyOrders(seller);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        assertThat(json.writeValueAsString(summary)).doesNotContain("mobileNumber", "buyerMobile");
        assertThat(json.writeValueAsString(drill)).doesNotContain("mobileNumber", "buyerMobile");
        assertThat(json.writeValueAsString(list)).doesNotContain("mobileNumber", "buyerMobile");
        assertThat(orders.getOrderDtoForSeller(orderId, seller).getBuyer().getMobileNumber())
                .isEqualTo(buyer.getMobileNumber());
        assertThatThrownBy(() -> orders.getOrderDtoForSeller(orderId, other))
                .isInstanceOf(SellerNotAuthorizedException.class);
    }

    @Test
    void deferredPaymentPersistsAndSellerPaidIsIdempotent() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(OrderService.DRAFT_ORDER_SESSION_KEY, orderId);
        session.setAttribute("BUYER_USER", buyerId);

        assertThat(orders.placeOrder(PaymentStatus.WILL_PAY_LATER, "gpay", null, null, session)
                .getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
        assertThat(orderRepo.findById(orderId).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
        assertThatThrownBy(() -> orders.markOrderAsPaid(orderId, other))
                .isInstanceOf(SellerNotAuthorizedException.class);
        assertThatThrownBy(() -> orders.markOrderAsPaid(999999L, seller))
                .isInstanceOf(OrderNotFoundException.class);

        long count = orderRepo.count();
        BigDecimal total = orderRepo.findById(orderId).orElseThrow().getTotalAmount();
        assertThat(orders.markOrderAsPaid(orderId, seller).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        OrderDto buyerView = orders.getOrderDetails(orderId, buyer);
        assertThat(buyerView.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(buyerView.getTotalAmount()).isEqualByComparingTo(total);
        assertThat(buyerView.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(buyerView.getItems().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(buyerView.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(admin.orderDetail(orderId).get("paymentStatus")).isEqualTo("PAID");
        assertThat(orderRepo.count()).isEqualTo(count);
        orders.markOrderAsPaid(orderId, seller);
        assertThat(orderRepo.count()).isEqualTo(count);
        assertThat(orderRepo.findById(orderId).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void cancelledAndLegacyDeferredAreSafe() {
        Order order = orderRepo.findById(orderId).orElseThrow();
        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepo.save(order);
        assertThatThrownBy(() -> orders.markOrderAsPaid(orderId, seller))
                .isInstanceOf(IllegalArgumentException.class);

        Kitchen kitchen = kitchens.findBySeller(seller).get(0);
        Product product = products.findById(productId).orElseThrow();
        Order legacy = new Order(buyer, kitchen);
        legacy.setOrderNumber("LEGACY-" + UUID.randomUUID());
        legacy.setOrderStatus(OrderStatus.ORDERED);
        legacy.setPaymentStatus(PaymentStatus.WILL_PAY_LATER);
        legacy.addItem(new OrderItem(product, 1, BigDecimal.valueOf(25)));
        legacy.recalculateTotal();
        orderRepo.save(legacy);
        assertThat(orders.markOrderAsPaid(legacy.getId(), seller).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
