package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.ProductUpdateDto;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Requirement 5 — editing a live offering that already has customer orders.
 *
 * <p>Once an offering has confirmed orders the seller may no longer change what
 * customers already agreed to (name, price, unit, offering date, Orders Open,
 * Delivery / Ready By). Orders Close is the single exception: it may only be
 * EXTENDED, never shortened. Draft carts are not real orders, so an abandoned
 * draft must not lock the seller out of their own offering.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:requirement-5;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class Requirement5LiveOfferingEditTest {

    /** Two days ahead keeps the offering inside the free pre-order baseline. */
    private static final int OFFERING_DAYS_AHEAD = 2;
    private static final String CLOSE = "21:00";

    @Autowired SellerService sellerService;
    @Autowired FeatureService featureService;
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;

    private User seller;
    private User buyer;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        // The "test" profile skips DataInitializer, so seed the feature catalogue
        // explicitly and give this seller a wide advance-days limit.
        featureService.ensureDefaults();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.saveAndFlush(seller);
        featureService.setSellerGrant(seller.getId(), FeatureService.KEY_MENU_ADVANCE_DAYS, true, 10);

        buyer = new User("Buyer" + suffix, "93" + suffix + "01", "A-1", UserRole.BUYER);
        buyer.setSociety("Society");
        buyer.setBuilding("A");
        buyer = users.saveAndFlush(buyer);

        kitchen = kitchens.saveAndFlush(new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller));
    }

    // ==================== "has orders" is decided by confirmed orders only ====================

    @Test
    void abandonedDraftCartDoesNotLockTheOffering() {
        Product product = productWithOrder(OrderStatus.DRAFT);

        ProductUpdateDto dto = new ProductUpdateDto();
        dto.setPrice(BigDecimal.valueOf(55));
        dto.setName("Poha Special");

        sellerService.updateProduct(product.getId(), dto, seller);

        Product reloaded = products.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(55));
        assertThat(reloaded.getName()).isEqualTo("Poha Special");
    }

    // ==================== confirmed orders freeze the agreed terms ====================

    @Test
    void confirmedOrderLocksNamePriceAndUnit() {
        Product product = productWithOrder(OrderStatus.CONFIRMED);

        ProductUpdateDto rename = new ProductUpdateDto();
        rename.setName("Renamed");
        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), rename, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot rename item after orders exist.");

        ProductUpdateDto reprice = new ProductUpdateDto();
        reprice.setPrice(BigDecimal.valueOf(99));
        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), reprice, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change price after orders exist.");

        ProductUpdateDto reunit = new ProductUpdateDto();
        reunit.setPriceUnit("kg");
        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), reunit, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change unit after orders exist.");
    }

    @Test
    void confirmedOrderLocksDateOrdersOpenAndReadyBy() {
        Product product = productWithOrder(OrderStatus.CONFIRMED);
        String offeringDate = product.getAvailableDate().toString();
        String movedDate = offeringDateOf(OFFERING_DAYS_AHEAD + 1).toString();

        // The whole offering is moved consistently so the timing rules pass and
        // the frozen-date rule is what rejects the edit.
        ProductUpdateDto moveDate = new ProductUpdateDto();
        moveDate.setAvailableDate(offeringDateOf(OFFERING_DAYS_AHEAD + 1));
        moveDate.setReadyByTime(movedDate + "T23:59");
        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), moveDate, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change availability date after orders exist.");

        ProductUpdateDto moveOpen = new ProductUpdateDto();
        moveOpen.setOrderWindowStart("08:00");
        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), moveOpen, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change order window start after orders exist.");

        ProductUpdateDto moveReady = new ProductUpdateDto();
        moveReady.setReadyByTime(offeringDate + "T22:00");
        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), moveReady, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change ready-by time after orders exist.");
    }

    // ==================== Orders Close may only be extended ====================

    @Test
    void ordersCloseCanBeExtendedAfterOrdersExist() {
        Product product = productWithOrder(OrderStatus.CONFIRMED);

        ProductUpdateDto dto = new ProductUpdateDto();
        dto.setOrderWindowEnd("23:00");

        sellerService.updateProduct(product.getId(), dto, seller);

        Product reloaded = products.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getOrderWindowEnd()).isEqualTo("23:00");
        // The legacy cutoff column stays aligned with the offering window.
        assertThat(reloaded.getCutoffTime()).isEqualTo("23:00");
    }

    @Test
    void ordersCloseCannotBeShortenedAfterOrdersExist() {
        Product product = productWithOrder(OrderStatus.CONFIRMED);

        ProductUpdateDto dto = new ProductUpdateDto();
        dto.setOrderWindowEnd("20:00");

        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), dto, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Orders Close can only be extended after orders exist (currently 21:00).");
        assertThat(products.findById(product.getId()).orElseThrow().getOrderWindowEnd()).isEqualTo(CLOSE);
    }

    @Test
    void legacyCutoffFieldFollowsTheSameExtensionRule() {
        Product product = productWithOrder(OrderStatus.CONFIRMED);

        ProductUpdateDto extend = new ProductUpdateDto();
        extend.setCutoffTime("23:30");
        sellerService.updateProduct(product.getId(), extend, seller);
        assertThat(products.findById(product.getId()).orElseThrow().getOrderWindowEnd()).isEqualTo("23:30");

        ProductUpdateDto shorten = new ProductUpdateDto();
        shorten.setCutoffTime("22:00");
        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), shorten, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Orders Close can only be extended after orders exist (currently 23:30).");
    }

    @Test
    void resubmittingTheSameOrdersCloseIsAlwaysAllowed() {
        Product product = productWithOrder(OrderStatus.CONFIRMED);

        ProductUpdateDto dto = new ProductUpdateDto();
        dto.setOrderWindowEnd(CLOSE);

        sellerService.updateProduct(product.getId(), dto, seller);

        assertThat(products.findById(product.getId()).orElseThrow().getOrderWindowEnd()).isEqualTo(CLOSE);
    }

    @Test
    void cancelledOrderStillCountsAsAnExistingOrder() {
        // A cancelled order was still accepted by the customer, so the agreed
        // price/name/date stay frozen; only inventory is restored.
        Product product = productWithOrder(OrderStatus.CANCELLED);

        ProductUpdateDto dto = new ProductUpdateDto();
        dto.setPrice(BigDecimal.valueOf(99));

        assertThatThrownBy(() -> sellerService.updateProduct(product.getId(), dto, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change price after orders exist.");
    }

    // ==================== the edit read model that feeds the Seller UI ====================

    @Test
    void editReadModelMarksDraftCartsAsUnlocked() {
        Product product = productWithOrder(OrderStatus.DRAFT);

        var edit = sellerService.getOfferingForEdit(product.getId(), seller);

        assertThat(edit.getId()).isEqualTo(product.getId());
        assertThat(edit.getHasOrders()).isFalse();
        assertThat(edit.getOrderCount()).isZero();
        assertThat(edit.getName()).isEqualTo("Poha");
        assertThat(edit.getAvailableDate()).isEqualTo(product.getAvailableDate());
        assertThat(edit.getOrderWindowEnd()).isEqualTo(CLOSE);
        assertThat(edit.getRemainingQuantity()).isEqualTo(8);
        assertThat(edit.getBookedQuantity()).isZero();
    }

    @Test
    void editReadModelReportsLiveOrdersAndTheLockedValues() {
        Product product = productWithOrder(OrderStatus.CONFIRMED);

        var edit = sellerService.getOfferingForEdit(product.getId(), seller);

        assertThat(edit.getHasOrders()).isTrue();
        assertThat(edit.getOrderCount()).isEqualTo(1);
        assertThat(edit.getName()).isEqualTo("Poha");
        assertThat(edit.getDescription()).isEqualTo("Hot poha");
        assertThat(edit.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(edit.getPriceUnit()).isEqualTo("plate");
        assertThat(edit.getAvailableDate()).isEqualTo(product.getAvailableDate());
        assertThat(edit.getOrderWindowStart()).isNull();
        assertThat(edit.getOrderWindowEnd()).isEqualTo(CLOSE);
        assertThat(edit.getReadyByTime()).isEqualTo(product.getAvailableDate() + "T23:59");
        assertThat(edit.getMaxQuantity()).isEqualTo(10);
        assertThat(edit.getRemainingQuantity()).isEqualTo(8);
        assertThat(edit.getIsPreorder()).isTrue();
        assertThat(edit.getLifecycleState()).isEqualTo("PRE_ORDER");
        assertThat(edit.getOrdersPaused()).isFalse();
        assertThat(edit.getSoldOut()).isFalse();
    }

    @Test
    void editReadModelFallsBackToTheLegacyCutoffColumn() {
        Product legacy = new Product(kitchen, "Legacy", "Old row", BigDecimal.valueOf(30), null);
        legacy.setPriceUnit("plate");
        legacy.setAvailableDate(LocalDate.now());
        legacy.setAvailableToday(true);
        legacy.setIsPreorder(false);
        legacy.setCutoffTime("23:00");
        legacy.setReadyByTime(LocalDate.now() + "T23:00");
        legacy.setMaxQuantity(5);
        legacy.setRemainingQuantity(5);
        legacy = products.saveAndFlush(legacy);

        var edit = sellerService.getOfferingForEdit(legacy.getId(), seller);

        assertThat(edit.getHasOrders()).isFalse();
        assertThat(edit.getOrderWindowEnd()).isEqualTo("23:00");
    }

    @Test
    void editReadModelIsOwnerScoped() {
        Product product = productWithOrder(OrderStatus.CONFIRMED);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User intruder = new User("Intruder" + suffix, "92" + suffix + "01", "X-1", UserRole.SELLER);
        intruder.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        User other = users.saveAndFlush(intruder);

        assertThatThrownBy(() -> sellerService.getOfferingForEdit(product.getId(), other))
                .isInstanceOf(SellerNotAuthorizedException.class);
    }

    // ==================== fixtures ====================

    private LocalDate offeringDateOf(int daysAhead) {
        return LocalDate.now().plusDays(daysAhead);
    }

    /** A pre-order offering (so timing validation is independent of the wall clock) that has one order. */
    private Product productWithOrder(OrderStatus orderStatus) {
        LocalDate offeringDate = offeringDateOf(OFFERING_DAYS_AHEAD);
        Product product = new Product(kitchen, "Poha", "Hot poha", BigDecimal.valueOf(40), null);
        product.setPriceUnit("plate");
        product.setAvailableDate(offeringDate);
        product.setAvailableToday(false);
        product.setIsPreorder(true);
        product.setOrderWindowStart(null);
        product.setOrderWindowEnd(CLOSE);
        product.setCutoffTime(CLOSE);
        product.setReadyByTime(offeringDate + "T23:59");
        product.setMaxQuantity(10);
        product.setRemainingQuantity(8);
        product = products.saveAndFlush(product);

        Order order = new Order(buyer, kitchen);
        order.setOrderNumber("R5-" + UUID.randomUUID());
        order.setOrderStatus(orderStatus);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.addItem(new OrderItem(product, 1, product.getPrice()));
        order.recalculateTotal();
        orders.saveAndFlush(order);
        return product;
    }
}
