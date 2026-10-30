package com.example.my_first_spring_api;

import com.example.my_first_spring_api.dto.ApiErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.UnexpectedRollbackException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionsReturnGenericMessageWithoutInternalDetails() {
        var response = handler.handleGenericException(
                new IllegalStateException("jdbc:postgresql://internal-host/schema-secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred. Please try again.");
        assertThat(body.getMessage()).doesNotContain("internal-host", "schema-secret");
    }

    @Test
    void unexpectedRollbackReturnsGenericMessageWithoutDatabaseDetails() {
        var response = handler.handleUnexpectedRollback(
                new UnexpectedRollbackException("transaction failed",
                        new IllegalStateException("relation customer_private does not exist")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("An unexpected error occurred. Please try again.")
                .doesNotContain("customer_private");
    }
}
