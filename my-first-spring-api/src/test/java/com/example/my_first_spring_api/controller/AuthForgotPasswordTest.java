package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.ForgotPasswordRequestDto;
import com.example.my_first_spring_api.service.AuthService;
import com.example.my_first_spring_api.service.BuyerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Regression coverage for the forgot-password / reset-password flow. */
class AuthForgotPasswordTest {

    private AuthService authService;
    private AuthController controller;
    private SecurityContextRepository securityContextRepository;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        controller = new AuthController(mock(BuyerService.class), authService, securityContextRepository);
        request = new MockHttpServletRequest();
    }

    @Test
    void emailRecoveryIsUnavailableWithoutLookingUpTheAccount() {
        ForgotPasswordRequestDto requestDto = new ForgotPasswordRequestDto();
        requestDto.setEmail("buyer@example.com");

        assertThatThrownBy(() -> controller.forgotPassword(requestDto, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(authService);
    }

    @Test
    void resetTokenEndpointIsDisabledUntilVerifiedDeliveryIsConfigured() {
        assertThatThrownBy(() -> controller.resetPassword())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(authService);
    }
}
