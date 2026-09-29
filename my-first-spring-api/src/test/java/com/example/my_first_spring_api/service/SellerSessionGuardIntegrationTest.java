package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0 regression - Seller dashboard rejected a valid Seller session with
 * "Only sellers can perform this action".
 *
 * The Buyer and Seller apps are served from the same origin, so they share a
 * single browser session. The Seller app authenticated once at boot and then
 * trusted that cached state, so as soon as a Buyer logged in on the same session
 * the Seller screen rendered the raw authorization error instead of restoring its
 * own seller session.
 *
 * These tests pin the server side of that contract, which must NOT change:
 *  - a seller session loads the dashboard;
 *  - a buyer session is still rejected by the seller endpoints;
 *  - re-running the seller demo-login restores seller access on the same session.
 *
 * The fix for the reported symptom is in seller.js (it must revalidate the
 * session with the server instead of trusting stale frontend state). The
 * backend authorization here is deliberately unchanged and must stay strict.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seller-session-guard;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SellerSessionGuardIntegrationTest {

    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired SellerAppService sellerAppService;
    @Autowired BuyerService buyerService;

    private User seller;
    private User buyer;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        seller = new User("Seller" + s, "91" + s + "01", "A-101", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setSociety("Sunshine Society");
        seller = users.saveAndFlush(seller);

        buyer = new User("Buyer" + s, "93" + s + "01", "A-1", UserRole.BUYER);
        buyer = users.saveAndFlush(buyer);

        kitchen = kitchens.saveAndFlush(new Kitchen("k" + s, "Kitchen " + s, "", null, seller));
        kitchen.setSociety("Sunshine Society");
        kitchen = kitchens.saveAndFlush(kitchen);
    }

    /** Mirrors SellerAppController.requireSeller(session). */
    private void assertSellerAccess(MockHttpSession session) {
        User user = buyerService.getCurrentBuyer(session);
        assertThat(user).isNotNull();
        if (user.getRole() != UserRole.SELLER) {
            throw new SellerNotAuthorizedException("Only sellers can perform this action");
        }
    }

    @Test
    void sellerSessionLoadsDashboard() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());

        assertSellerAccess(session);
        assertThat(sellerAppService.getDashboard(seller).getKitchenName()).isNotBlank();
    }

    @Test
    void buyerSessionIsStillRejectedBySellerEndpoints() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());

        // This rejection is correct and must not be weakened.
        assertThatThrownBy(() -> assertSellerAccess(session))
                .isInstanceOf(SellerNotAuthorizedException.class)
                .hasMessageContaining("Only sellers can perform this action");
    }

    @Test
    void buyerLoginClobbersTheSharedSessionWhichIsWhyTheFrontendMustRevalidate() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());
        assertSellerAccess(session); // seller session works

        // A Buyer login on the SAME session replaces the identity.
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        assertThatThrownBy(() -> assertSellerAccess(session))
                .isInstanceOf(SellerNotAuthorizedException.class);

        // Re-running the seller demo-login restores seller access on that session.
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());
        assertSellerAccess(session);
    }

    @Test
    void unauthenticatedSessionIsRejected() {
        MockHttpSession session = new MockHttpSession();
        assertThat(buyerService.getCurrentBuyer(session)).isNull();
    }
}
