package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.AuthResponseDto;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.service.BuyerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.context.SecurityContextRepository;
import com.example.my_first_spring_api.service.DemoAuthService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0 DEMO BLOCKER regression (auth payload).
 *
 * /api/auth/me and credential-based /api/auth/login build the client-side identity state
 * (state.user). That payload MUST carry the buyer's persisted society/building.
 * When it did not, the buyer UI had no service area, the Profile screen fell
 * back to a hardcoded placeholder society, and saving it overwrote the real
 * persisted society - after which the (correct) service-area validation
 * rejected the buyer's own kitchen.
 */
class AuthControllerProfileStateTest {

    private BuyerService buyerService;
    private SecurityContextRepository securityContextRepository;
    private AuthController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        buyerService = mock(BuyerService.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        controller = new AuthController(buyerService, mock(DemoAuthService.class), securityContextRepository);
        request = new MockHttpServletRequest();
    }

    private User buyerWithProfile() {
        User buyer = new User("Aarav Mehta", "9876500001", "A-402", UserRole.BUYER);
        buyer.setId(19L);
        buyer.setSociety("Sunshine Society");
        buyer.setBuilding("A Wing");
        return buyer;
    }

    @Test
    void authMeCarriesPersistedSocietyAndBuilding() {
        when(buyerService.getCurrentBuyer(request.getSession(false))).thenReturn(buyerWithProfile());

        AuthResponseDto dto = controller.me(request).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.isAuthenticated()).isTrue();
        // These two fields are what the client renders and re-saves.
        assertThat(dto.getSociety()).isEqualTo("Sunshine Society");
        assertThat(dto.getBuilding()).isEqualTo("A Wing");
    }

    @Test
    void unauthenticatedResponseHasNoSocietySoTheClientCannotInventOne() {
        when(buyerService.getCurrentBuyer(any())).thenReturn(null);

        AuthResponseDto dto = controller.me(request).getBody();

        assertThat(dto.isAuthenticated()).isFalse();
        assertThat(dto.getSociety()).isNull();
        assertThat(dto.getBuilding()).isNull();
    }
}
