package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-sim-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
public class OrderPlacementProductionSimulationTest {

    @Autowired UserRepository users;
    @Autowired AreaRepository areas;
    @Autowired SocietyRepository societies;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired TransactionTemplate txTemplate;
    @Autowired EntityManager em;

    @Test
    public void testPlaceOrderLikeProduction() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Area area = areas.saveAndFlush(new Area("Area " + suffix));
        Society society = societies.saveAndFlush(new Society(area, "Society " + suffix));

        User seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setAreaRef(area);
        seller.setSocietyRef(society);
        seller = users.saveAndFlush(seller);

        Kitchen kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen.setSociety(society.getName());
        kitchen.setAvailableToday(true);
        kitchen.getServedSocieties().add(society);
        kitchen = kitchens.saveAndFlush(kitchen);

        User buyer = new User("Buyer" + suffix, "93" + suffix + "01", "A-402", UserRole.BUYER);
        buyer.setAreaRef(area);
        buyer.setSocietyRef(society);
        buyer.setSociety(society.getName());
        buyer.setBuilding("Tower A");
        buyer = users.saveAndFlush(buyer);

        Product product = new Product(kitchen, "Biryani", "desc", BigDecimal.valueOf(250), null);
        product.setAvailableToday(true);
        product.setAvailableDate(LocalDate.now());
        product.setCutoffTime("23:59");
        product.setOrderWindowEnd("23:59");
        product.setReadyByTime(LocalDate.now().atTime(23, 59).toString());
        product.setRemainingQuantity(50);
        product.setMaxQuantity(50);
        product = products.saveAndFlush(product);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);

        Long kId = kitchen.getId();
        // Transaction 1: create draft
        txTemplate.execute(status -> {
            orderService.createOrUpdateDraftOrder(kId, List.of(req), session);
            return null;
        });

        em.clear();

        // Transaction 2: place order (completely separate transaction, realistic HTTP call)
        PlaceOrderRequest.BuyerDetails buyerDetails = new PlaceOrderRequest.BuyerDetails();
        buyerDetails.setName("Buyer" + suffix);
        buyerDetails.setMobileNumber(buyer.getMobileNumber());
        buyerDetails.setSociety(society.getName());
        buyerDetails.setSocietyId(society.getId());
        buyerDetails.setBuilding("Tower A");
        buyerDetails.setFlatHouseNumber("A-402");

        txTemplate.execute(status -> {
            var placed = orderService.placeOrder(PaymentStatus.PENDING, null, buyerDetails, "spicy", session);
            assertThat(placed).isNotNull();
            assertThat(placed.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
            return null;
        });
    }

    @Test
    public void testPlaceOrderWithPaymentMethod() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Area area = areas.saveAndFlush(new Area("Area " + suffix));
        Society society = societies.saveAndFlush(new Society(area, "Society " + suffix));

        User seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setAreaRef(area);
        seller.setSocietyRef(society);
        seller = users.saveAndFlush(seller);

        Kitchen kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen.setSociety(society.getName());
        kitchen.setAvailableToday(true);
        kitchen.getServedSocieties().add(society);
        kitchen = kitchens.saveAndFlush(kitchen);

        User buyer = new User("Buyer" + suffix, "93" + suffix + "01", "A-402", UserRole.BUYER);
        buyer.setAreaRef(area);
        buyer.setSocietyRef(society);
        buyer.setSociety(society.getName());
        buyer.setBuilding("Tower A");
        buyer = users.saveAndFlush(buyer);

        Product product = new Product(kitchen, "Biryani", "desc", BigDecimal.valueOf(250), null);
        product.setAvailableToday(true);
        product.setAvailableDate(LocalDate.now());
        product.setCutoffTime("23:59");
        product.setOrderWindowEnd("23:59");
        product.setReadyByTime(LocalDate.now().atTime(23, 59).toString());
        product.setRemainingQuantity(50);
        product.setMaxQuantity(50);
        product = products.saveAndFlush(product);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);

        Long kId = kitchen.getId();
        // Transaction 1: create draft
        txTemplate.execute(status -> {
            orderService.createOrUpdateDraftOrder(kId, List.of(req), session);
            return null;
        });

        em.clear();

        PlaceOrderRequest.BuyerDetails buyerDetails = new PlaceOrderRequest.BuyerDetails();
        buyerDetails.setName("Buyer" + suffix);
        buyerDetails.setMobileNumber(buyer.getMobileNumber());
        buyerDetails.setSociety(society.getName());
        buyerDetails.setSocietyId(society.getId());
        buyerDetails.setBuilding("Tower A");
        buyerDetails.setFlatHouseNumber("A-402");

        txTemplate.execute(status -> {
            var placed = orderService.placeOrder(PaymentStatus.PENDING, "card", buyerDetails, "spicy", session);
            assertThat(placed).isNotNull();
            assertThat(placed.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
            return null;
        });
    }
}
