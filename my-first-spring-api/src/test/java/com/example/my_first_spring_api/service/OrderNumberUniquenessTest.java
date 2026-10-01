package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the order-number generator.
 *
 * <p>The previous implementation was {@code "SM" + System.nanoTime() % 10000000000L}.
 * That modulus makes the value <b>cycle every 10 seconds</b>, so two orders created
 * ten seconds apart were assigned the same number - and with the UNIQUE constraint on
 * {@code order_number} the second insert failed, breaking order creation outright.
 *
 * <p>These tests pin the replacement behaviour end-to-end against a real database:
 * the committed format, distinctness across a rapid burst of orders, persistence of
 * the value, and the UNIQUE constraint still acting as the final backstop.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-number-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class OrderNumberUniquenessTest {

    /** The committed contract: SM- followed by 12 uppercase hex characters. */
    private static final String COMMITTED_FORMAT = "^SM-[0-9A-F]{12}$";

    @Autowired private OrderService orders;
    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orderRepo;
    @Autowired private PlatformTransactionManager tx;

    private Long buyerId;
    private Long kitchenId;
    private Long productId;

    @BeforeEach
    void setup() {
        String s = UUID.randomUUID().toString().substring(0, 4);
        Long[] ids = new TransactionTemplate(tx).execute(x -> {
            User seller = users.save(new User("Seller" + s, "91" + s + "0001", null, UserRole.SELLER));
            seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
            users.save(seller);

            User buyer = users.save(new User("Buyer" + s, "93" + s + "0001", "A-1", UserRole.BUYER));
            buyer.setSociety("Test Society");
            buyer.setBuilding("A");
            buyer.setFlatHouseNumber("101");
            users.save(buyer);

            Kitchen kitchen = kitchens.save(new Kitchen("k" + s, "Kitchen " + s, "", null, seller));
            kitchen.setAvailableToday(true);
            kitchens.save(kitchen);

            Product product = products.save(new Product(kitchen, "Poha", "", BigDecimal.valueOf(25), null));
            product.setAvailableToday(true);
            product.setRemainingQuantity(1000);
            products.save(product);

            return new Long[]{buyer.getId(), kitchen.getId(), product.getId()};
        });
        buyerId = ids[0];
        kitchenId = ids[1];
        productId = ids[2];
    }

    /** Places one complete order through the real checkout path and returns its number. */
    private String placeOneOrder() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyerId);
        orders.createOrUpdateDraftOrder(kitchenId, List.of(item()), session);
        return orders.placeOrder(PaymentStatus.WILL_PAY_LATER, null, null, session).getOrderNumber();
    }

    private OrderItemRequest item() {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);
        return item;
    }

    // ---------------- regression tests ----------------

    @Test
    void everyGeneratedOrderNumberMatchesTheCommittedFormat() {
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < 6; i++) numbers.add(placeOneOrder());

        for (String n : numbers) {
            assertThat(n)
                    .as("order number '%s' must match %s", n, COMMITTED_FORMAT)
                    .matches(COMMITTED_FORMAT);
        }
    }

    @Test
    void aRapidBurstOfOrdersProducesDistinctNumbers() {
        // The old modulus generator cycled every 10 seconds; a burst of orders must
        // never collide, and no insert may fail on the UNIQUE order_number column.
        Set<String> numbers = new LinkedHashSet<>();
        for (int i = 0; i < 12; i++) numbers.add(placeOneOrder());

        assertThat(numbers).hasSize(12);
    }

    @Test
    void generatedNumbersArePersistedAndReloadable() {
        List<String> generated = new ArrayList<>();
        for (int i = 0; i < 5; i++) generated.add(placeOneOrder());

        for (String n : generated) {
            assertThat(orderRepo.existsByOrderNumber(n))
                    .as("'%s' must be readable back from the database", n)
                    .isTrue();
        }
        assertThat(orderRepo.findAll())
                .extracting(Order::getOrderNumber)
                .containsAll(generated);
    }

    @Test
    void theUniqueConstraintStillRejectsADuplicateNumber() {
        String first = placeOneOrder();
        Order existing = orderRepo.findAll().stream()
                .filter(o -> first.equals(o.getOrderNumber()))
                .findFirst().orElseThrow();

        // The generator must never produce a taken value...
        assertThat(orderRepo.existsByOrderNumber(first)).isTrue();

        // ...and the UNIQUE column remains the final backstop if it ever did.
        Order duplicate = new Order(existing.getBuyer(), existing.getKitchen());
        duplicate.setOrderStatus(OrderStatus.DRAFT);
        duplicate.setOrderNumber(first);
        duplicate.addItem(new OrderItem(products.findById(productId).orElseThrow(), 1, BigDecimal.valueOf(25)));
        duplicate.recalculateTotal();

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> orderRepo.saveAndFlush(duplicate));
    }
}
