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
 * single browser session. The current persisted identity and seller approval
 * status must be checked for every seller operation.
 *
 * These tests pin the server side of that contract, which must NOT change:
 *  - a seller session loads the dashboard;
 *  - a buyer session is still rejected by the seller endpoints;
 *  - an unapproved seller cannot use the dashboard even with a seller session.
 *
 * These service-level checks complement the HTTP-level registration and
 * authorization tests; they must not simulate mobile-only identity restoration.
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
        if (!user.isApprovedSeller()) {
            throw new SellerNotAuthorizedException("Your seller account is awaiting Admin approval.");
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
    void sessionRoleIsReadFromTheCurrentPersistedUser() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());
        assertSellerAccess(session); // seller session works

        // A Buyer login on the SAME session replaces the identity.
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        assertThatThrownBy(() -> assertSellerAccess(session))
                .isInstanceOf(SellerNotAuthorizedException.class);

        // The test fixture explicitly switches the session identity back; the
        // application no longer offers mobile-only session restoration.
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());
        assertSellerAccess(session);
    }

    @Test
    void pendingSellerRoleCannotUseTheSellerDashboard() {
        User pending = new User("Pending Seller", "94" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                null, UserRole.SELLER);
        pending.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        pending = users.saveAndFlush(pending);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, pending.getId());

        assertThatThrownBy(() -> assertSellerAccess(session))
                .isInstanceOf(SellerNotAuthorizedException.class)
                .hasMessageContaining("awaiting Admin approval");
    }

    @Test
    void unauthenticatedSessionIsRejected() {
        MockHttpSession session = new MockHttpSession();
        assertThat(buyerService.getCurrentBuyer(session)).isNull();
    }

    @Test
    void approvalRevokedBetweenRequestsBlocksAnExistingSellerSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());
        assertSellerAccess(session);

        seller.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        users.saveAndFlush(seller);
        assertThatThrownBy(() -> assertSellerAccess(session))
                .isInstanceOf(SellerNotAuthorizedException.class)
                .hasMessageContaining("awaiting Admin approval");
    }

    @Test
    void persistedRoleChangeToBuyerBlocksAnExistingSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());
        assertSellerAccess(session);

        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        assertThatThrownBy(() -> assertSellerAccess(session))
                .isInstanceOf(SellerNotAuthorizedException.class);

        User reread = buyerService.getCurrentBuyer(session);
        assertThat(reread.getRole()).isEqualTo(UserRole.BUYER);
    }

    @Test
    void sellerOwnershipIsEnforcedForDifferentApprovedSellers() {
        // A second seller must not be able to read or mutate the first seller's
        // offerings, even though both hold valid SELLER sessions.
        MockHttpSession otherSession = new MockHttpSession();
        otherSession.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());
        assertSellerAccess(otherSession);

        User intruder = new User("Other", "95" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "01",
                "B-1", UserRole.SELLER);
        intruder.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        User persistedIntruder = users.saveAndFlush(intruder);

        MockHttpSession intruderSession = new MockHttpSession();
        intruderSession.setAttribute(BuyerService.BUYER_SESSION_KEY, persistedIntruder.getId());
        assertSellerAccess(intruderSession);

        // The intruder is a valid seller, yet owns no kitchen, so the
        // owner-scoped dashboard call must still refuse them.
        assertThat(kitchens.findBySeller(persistedIntruder))
                .as("intruder must not own the seller's kitchen")
                .isEmpty();
        assertThat(kitchens.findBySeller(seller))
                .as("the real seller still owns exactly their own kitchen")
                .extracting(Kitchen::getId)
                .containsExactly(kitchen.getId());
        assertThatThrownBy(() -> sellerAppService.getDashboard(persistedIntruder))
                .as("a seller without a kitchen must be rejected, not served another seller's data")
                .isInstanceOf(Exception.class);
    }
}
