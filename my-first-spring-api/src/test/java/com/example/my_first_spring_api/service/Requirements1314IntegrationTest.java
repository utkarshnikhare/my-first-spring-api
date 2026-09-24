package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:requirements-13-14;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class Requirements1314IntegrationTest {
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired SellerAppService sellerAppService;

    @Test
    void cancellationKeepsPaymentAndRestoresInventoryExactlyOnce() {
        Fixture f = fixture(2, 1);
        Order order = order(f, OrderStatus.ORDERED, PaymentStatus.PENDING, 1, f.product.getPrice());
        orderService.cancelOrder(order.getId(), f.buyer);
        Order cancelled = orders.findById(order.getId()).orElseThrow();
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(cancelled.getTotalAmount()).isEqualByComparingTo("40.00");
        var cancelledDto = orderService.getOrderDetails(order.getId(), f.buyer);
        assertThat(cancelledDto.getItems().get(0).getQuantity()).isEqualTo(1);
        assertThat(cancelledDto.getItems().get(0).getPrice()).isEqualByComparingTo("40.00");
        assertThat(products.findById(f.product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(2);
        orderService.cancelOrder(order.getId(), f.buyer);
        assertThat(products.findById(f.product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    void sellerCancellationPreservesPaidPaymentStatus() {
        Fixture f = fixture(3, 1);
        Order order = order(f, OrderStatus.CONFIRMED, PaymentStatus.PAID, 1, f.product.getPrice());
        orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED, f.seller);
        Order cancelled = orders.findById(order.getId()).orElseThrow();
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(products.findById(f.product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    void historyUsesOfferingDateAndIsSortedWithoutCurrentOrFutureItems() {
        Fixture f = fixture(5, 0);
        Product older = product(f.kitchen, "Older", LocalDate.now().minusDays(3), 2, 1);
        Product yesterday = product(f.kitchen, "Yesterday", LocalDate.now().minusDays(1), 3, 2);
        Product today = product(f.kitchen, "Today", LocalDate.now(), 4, 3);
        Product future = product(f.kitchen, "Future", LocalDate.now().plusDays(1), 5, 4);
        List<com.example.my_first_spring_api.dto.ProductDto> history = sellerAppService.getRecentOfferings(f.seller);
        assertThat(history).extracting(com.example.my_first_spring_api.dto.ProductDto::getId)
                .containsExactly(yesterday.getId(), older.getId());
        assertThat(history).extracting(com.example.my_first_spring_api.dto.ProductDto::getName)
                .doesNotContain(today.getName(), future.getName());
        assertThat(history.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void republishCreatesIndependentNewOfferingAndPreservesOldState() {
        Fixture f = fixture(6, 0);
        Product old = product(f.kitchen, "Old Poha", LocalDate.now().minusDays(1), 3, 0);
        old.setOrdersPaused(true); old.setOrderWindowEnd("23:59"); old.setCutoffTime("23:59");
        old.setReadyByTime(LocalDate.now().minusDays(1) + "T23:59");
        products.saveAndFlush(old);
        var republished = sellerAppService.batchRepublish(List.of(old.getId()), LocalDate.now(), f.seller).get(0);
        Product oldAfter = products.findById(old.getId()).orElseThrow();
        Product newAfter = products.findById(republished.getId()).orElseThrow();
        assertThat(republished.getId()).isNotEqualTo(old.getId());
        assertThat(oldAfter.getRemainingQuantity()).isZero();
        assertThat(oldAfter.isOrdersPaused()).isTrue();
        assertThat(newAfter.getRemainingQuantity()).isEqualTo(3);
        assertThat(newAfter.isOrdersPaused()).isFalse();
        assertThat(newAfter.getOrderWindowStart()).isNull();
        assertThat(newAfter.getAvailableDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void sellerCannotRepublishAnotherSellersOffering() {
        Fixture owner = fixture(7, 0); Fixture other = fixture(8, 0);
        Product old = product(owner.kitchen, "Private item", LocalDate.now().minusDays(1), 2, 1);
        assertThatThrownBy(() -> sellerAppService.batchRepublish(List.of(old.getId()), LocalDate.now(), other.seller))
                .isInstanceOf(SellerNotAuthorizedException.class);
    }

    private Fixture fixture(int max, int remaining) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User seller = new User("Seller" + suffix, "91" + suffix + "0001", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED); seller = users.save(seller);
        User buyer = new User("Buyer" + suffix, "93" + suffix + "0001", "A-1", UserRole.BUYER);
        buyer = users.save(buyer);
        Kitchen kitchen = kitchens.save(new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller));
        Product product = new Product(kitchen, "Poha" + suffix, "", BigDecimal.valueOf(40), null);
        product.setAvailableToday(true); product.setMaxQuantity(max);
        product.setRemainingQuantity(remaining); product.setBookedQuantity(max - remaining);
        return new Fixture(seller, buyer, kitchen, products.saveAndFlush(product));
    }

    private Product product(Kitchen kitchen, String name, LocalDate date, int max, int remaining) {
        Product p = new Product(kitchen, name, "", BigDecimal.valueOf(40), null);
        p.setAvailableDate(date); p.setAvailableToday(date.equals(LocalDate.now()));
        p.setMaxQuantity(max); p.setRemainingQuantity(remaining);
        return products.saveAndFlush(p);
    }

    private Order order(Fixture f, OrderStatus status, PaymentStatus payment, int qty, BigDecimal price) {
        Order o = new Order(f.buyer, f.kitchen); o.setOrderNumber("REQ-" + UUID.randomUUID());
        o.setOrderStatus(status); o.setPaymentStatus(payment);
        o.addItem(new OrderItem(f.product, qty, price)); o.recalculateTotal();
        return orders.saveAndFlush(o);
    }

    private record Fixture(User seller, User buyer, Kitchen kitchen, Product product) {}
}
