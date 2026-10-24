package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.ForgotPasswordRequestDto;
import com.example.my_first_spring_api.dto.ResetPasswordRequestDto;
import com.example.my_first_spring_api.exception.InvalidCredentialsException;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.service.AuthService;
import com.example.my_first_spring_api.service.BuyerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void forgotPasswordReturnsResetLinkForKnownEmail() {
        ForgotPasswordRequestDto requestDto = new ForgotPasswordRequestDto();
        requestDto.setEmail("buyer@example.com");

        when(authService.requestPasswordReset("buyer@example.com"))
                .thenReturn(new AuthService.PasswordResetResponseDto(
                        "If an account exists for 'buyer@example.com', a single-use reset link has been issued.",
                        "http://example.com/reset-password?token=XYZ"));

        var result = controller.forgotPassword(requestDto, null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().toString())
                .contains("If an account exists")
                .contains("http://example.com/reset-password?token=XYZ");
    }

    @Test
    void forgotPasswordRejectsUnknownEmail() {
        ForgotPasswordRequestDto requestDto = new ForgotPasswordRequestDto();
        requestDto.setEmail("missing@example.com");

        when(authService.requestPasswordReset("missing@example.com"))
                .thenThrow(new InvalidCredentialsException("No account is registered with that e-mail address."));

        assertThatThrownBy(() -> controller.forgotPassword(requestDto, null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("No account is registered");
    }

    @Test
    void resetPasswordWithValidTokenSucceeds() {
        String token = "abcd1234";
        User user = new User("Alice", "9000000001", null, UserRole.BUYER);
        user.setId(5L);

        ResetPasswordRequestDto requestDto = new ResetPasswordRequestDto();
        requestDto.setToken(token);
        requestDto.setNewPassword("Str0ngPass!");

        when(authService.resetPassword(eq(token), eq("Str0ngPass!")))
                .thenAnswer(invocation -> user);

        var result = controller.resetPassword(requestDto);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().toString()).contains("Password has been reset");
    }
}
