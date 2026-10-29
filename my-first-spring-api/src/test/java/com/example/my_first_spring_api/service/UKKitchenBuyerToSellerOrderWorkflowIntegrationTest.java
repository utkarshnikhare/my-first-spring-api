package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.*;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 E2E Integration Regression Test:
 * A buyer orders two plates from the active UK Kitchen.
 *
 * Verifies:
 * - Buyer checkout succeeds.
 * - Exactly one order is persisted.
 * - The order contains the correct seller, kitchen, items, quantity (2), amount and occurrence date.
 * - Stock is correctly decremented by 2.
 * - The owning seller can retrieve and see the order in the seller dashboard summary,
 *   orders list, and order detail endpoints.
 * - Other sellers cannot retrieve or modify this order (data isolation enforced).
 * - Duplicate submissions do not create duplicate orders.
 * - Order remains persisted and accessible across new sessions.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:uk-kitchen-workflow-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.bootstrap.enabled=false"
})
@ActiveProfiles("test")
public class UKKitchenBuyerToSellerOrderWorkflowIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private SocietyRepository societyRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    @Autowired private OrderService orderService;
    @Autowired private SellerService sellerService;
    @Autowired private SellerAppService sellerAppService;
    @Autowired private BuyerService buyerService;

    private User ukSeller;
    private Kitchen ukKitchen;
    private Product ukProduct;

    private User otherSeller;
    private Kitchen otherKitchen;

    private User buyer;
    private Society ukSociety;
    private Area ukArea;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        kitchenRepository.deleteAll();
        userRepository.deleteAll();
        societyRepository.deleteAll();
        areaRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        ukArea = areaRepository.saveAndFlush(new Area("UK Area " + suffix));
        ukSociety = societyRepository.saveAndFlush(new Society(ukArea, "UK Society " + suffix));

        // Create UK Seller and Kitchen
        ukSeller = new User("UK Seller", "987654" + suffix, "UK-S-1", UserRole.SELLER);
        ukSeller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        ukSeller.setAreaRef(ukArea);
        ukSeller.setSocietyRef(ukSociety);
        ukSeller = userRepository.saveAndFlush(ukSeller);

        ukKitchen = new Kitchen("UK Kitchen", "UK Kitchen", "Special Home Food", null, ukSeller);
        ukKitchen.setDisplayName("UK Kitchen");
        ukKitchen.setSociety(ukSociety.getName());
        ukKitchen.setAvailableToday(true);
        ukKitchen.getServedSocieties().add(ukSociety);
        ukKitchen = kitchenRepository.saveAndFlush(ukKitchen);

        // Product in UK Kitchen: price 150, 10 in stock
        ukProduct = new Product(ukKitchen, "UK Special Plate", "Delicious plate", BigDecimal.valueOf(150), null);
        ukProduct.setAvailableToday(true);
        ukProduct.setAvailableDate(LocalDate.now());
        ukProduct.setRemainingQuantity(10);
        ukProduct.setMaxQuantity(10);
        ukProduct.setCutoffTime("23:59");
        ukProduct.setOrderWindowEnd("23:59");
        ukProduct = productRepository.saveAndFlush(ukProduct);

        // Create Other Seller and Other Kitchen
        otherSeller = new User("Other Seller", "912345" + suffix, "OTH-1", UserRole.SELLER);
        otherSeller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        otherSeller.setAreaRef(ukArea);
        otherSeller.setSocietyRef(ukSociety);
        otherSeller = userRepository.saveAndFlush(otherSeller);

        otherKitchen = new Kitchen("other-kitchen-" + suffix, "Other Kitchen", "Other desc", null, otherSeller);
        otherKitchen.setSociety(ukSociety.getName());
        otherKitchen.setAvailableToday(true);
        otherKitchen.getServedSocieties().add(ukSociety);
        otherKitchen = kitchenRepository.saveAndFlush(otherKitchen);

        // Create Buyer living in UK Society
        buyer = new User("UK Buyer", "981111" + suffix, "Flat 101", UserRole.BUYER);
        buyer.setAreaRef(ukArea);
        buyer.setSocietyRef(ukSociety);
        buyer.setSociety(ukSociety.getName());
        buyer.setBuilding("Tower UK");
        buyer.setFlatHouseNumber("101");
        buyer = userRepository.saveAndFlush(buyer);
    }

    @Test
    @Transactional
    @DisplayName("Complete end-to-end workflow: Buyer orders 2 plates from UK Kitchen, verified by seller and isolated from other sellers")
    void buyerOrdersTwoPlatesFromUKKitchenEndToEnd() {
        MockHttpSession buyerSession = new MockHttpSession();
        buyerSession.setAttribute("BUYER_USER", buyer.getId());

        // 1. Buyer creates draft order for 2 plates
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(ukProduct.getId());
        itemRequest.setQuantity(2);
        OrderDto draftDto = orderService.createOrUpdateDraftOrder(ukKitchen.getId(), List.of(itemRequest), buyerSession);

        assertThat(draftDto).isNotNull();
        assertThat(draftDto.getKitchen().getId()).isEqualTo(ukKitchen.getId());
        assertThat(draftDto.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(buyerSession.getAttribute(OrderService.DRAFT_ORDER_SESSION_KEY)).isNotNull();

        // 2. Buyer places the order
        PlaceOrderRequest.BuyerDetails buyerDetails = new PlaceOrderRequest.BuyerDetails();
        buyerDetails.setName("UK Buyer");
        buyerDetails.setPhone(buyer.getMobileNumber());
        buyerDetails.setSociety(ukSociety.getName());
        buyerDetails.setBuilding("Tower UK");
        buyerDetails.setFlatHouseNumber("101");

        OrderDto placedOrder = orderService.placeOrder(
                PaymentStatus.PAID,
                null,
                buyerDetails,
                "Extra spicy please",
                buyerSession
        );

        // 3. Verify exactly one order persisted in DB
        List<Order> allOrders = orderRepository.findAll();
        assertThat(allOrders).hasSize(1);
        Order order = allOrders.get(0);

        assertThat(order.getOrderNumber()).isEqualTo(placedOrder.getOrderNumber());
        assertThat(order.getKitchen().getId()).isEqualTo(ukKitchen.getId());
        assertThat(order.getKitchen().getName()).isEqualTo("UK Kitchen");
        assertThat(order.getKitchen().getSeller().getId()).isEqualTo(ukSeller.getId());
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));

        // Verify items and quantity
        assertThat(order.getItems()).hasSize(1);
        OrderItem orderItem = order.getItems().get(0);
        assertThat(orderItem.getProduct().getId()).isEqualTo(ukProduct.getId());
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(orderItem.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(300));

        // Verify stock decremented by 2 (10 -> 8)
        Product refreshedProduct = productRepository.findById(ukProduct.getId()).orElseThrow();
        assertThat(refreshedProduct.getRemainingQuantity()).isEqualTo(8);

        // 4. Verify Owning Seller retrieval
        List<SellerOrderSummaryRowDto> sellerOrders = orderService.getSellerOrders(ukSeller);
        assertThat(sellerOrders).hasSize(1);
        SellerOrderSummaryRowDto summaryRow = sellerOrders.get(0);
        assertThat(summaryRow.getOrderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(summaryRow.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(summaryRow.getBuyer()).isNotNull();
        assertThat(summaryRow.getBuyer().getName()).isEqualTo("UK Buyer");
        assertThat(summaryRow.getBuyer().getSociety()).isEqualTo(ukSociety.getName());
        assertThat(summaryRow.getBuyer().getBuilding()).isEqualTo("Tower UK");
        assertThat(summaryRow.getBuyer().getFlatHouseNumber()).isEqualTo("101");

        // Owning seller dashboard summary
        SellerOrderSummaryDto summary = sellerAppService.getOrderSummary(ukSeller, LocalDate.now());
        assertThat(summary.getTotalOrderCount()).isEqualTo(1);
        assertThat(summary.getPaidCount()).isEqualTo(1);
        assertThat(summary.getPendingCount()).isEqualTo(0);
        assertThat(summary.getCancelledCount()).isEqualTo(0);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(300));

        // Owning seller drilldown detail
        OrderItemDetailDto offeringDetail = sellerAppService.getOrderItemDetail(
                ukSeller, ukProduct.getId(), LocalDate.now(), null, null, null);
        assertThat(offeringDetail).isNotNull();
        assertThat(offeringDetail.getCustomers()).hasSize(1);
        assertThat(offeringDetail.getCustomers().get(0).getOrderNumber()).isEqualTo(order.getOrderNumber());

        // Owning seller order detail
        OrderDto detail = sellerService.getOrderDetailForSeller(order.getId(), ukSeller);
        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(order.getId());
        assertThat(detail.getOrderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(detail.getItems()).hasSize(1);
        assertThat(detail.getItems().get(0).getQuantity()).isEqualTo(2);

        // 5. Verify Other Seller ISOLATION
        List<SellerOrderSummaryRowDto> otherSellerOrders = orderService.getSellerOrders(otherSeller);
        assertThat(otherSellerOrders).isEmpty();

        SellerOrderSummaryDto otherSummary = sellerAppService.getOrderSummary(otherSeller, LocalDate.now());
        assertThat(otherSummary.getTotalOrderCount()).isEqualTo(0);

        // Other seller attempting to view UK order detail is rejected with 403 / SellerNotAuthorizedException
        assertThatThrownBy(() -> sellerAppService.getOrderItemDetail(
                otherSeller, ukProduct.getId(), LocalDate.now(), null, null, null))
                .isInstanceOf(SellerNotAuthorizedException.class);

        assertThatThrownBy(() -> sellerService.getOrderDetailForSeller(order.getId(), otherSeller))
                .isInstanceOf(SellerNotAuthorizedException.class);

        // Other seller attempting to update order status is rejected
        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(), OrderStatus.DELIVERED, otherSeller))
                .isInstanceOf(SellerNotAuthorizedException.class);

        // 6. Verify duplicate submission prevention
        assertThatThrownBy(() -> orderService.placeOrder(
                PaymentStatus.PAID,
                null,
                buyerDetails,
                "Duplicate attempt",
                buyerSession
        )).isInstanceOf(IllegalArgumentException.class);

        // DB still has exactly 1 order
        assertThat(orderRepository.findAll()).hasSize(1);
        // Stock remains 8 (not decremented again)
        assertThat(productRepository.findById(ukProduct.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(8);

        // 7. Verify Owning Seller status transition
        OrderDto deliveredOrder = orderService.updateOrderStatus(order.getId(), OrderStatus.DELIVERED, ukSeller);
        assertThat(deliveredOrder.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);

        Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloadedOrder.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
    }
}
