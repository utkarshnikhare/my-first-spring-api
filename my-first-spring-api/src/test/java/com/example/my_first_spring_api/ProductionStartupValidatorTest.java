package com.example.my_first_spring_api;
 
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
 
import static org.assertj.core.api.Assertions.assertThat;
 
/**
 * Tests for {@link ProductionStartupValidator} to ensure it fails fast with clear messages
 * when production environment variables are missing or unresolved.
 */
class ProductionStartupValidatorTest {
 
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProductionStartupValidator.class);
 
    @Test
    void shouldPassWhenValidProdConfigIsProvided() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "spring.datasource.url=jdbc:postgresql://localhost:5432/db",
                "spring.datasource.username=user",
                "spring.datasource.password=pass",
                "sociomart.auth.admin-password=admin123",
                "sociomart.auth.super-admin-password=super123"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ProductionStartupValidator.class);
            });
    }
 
    @Test
    void shouldFailWhenJdbcUrlIsLiteralPlaceholder() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "spring.datasource.url=${SOCIOMART_DB_JDBC_URL}",
                "spring.datasource.username=user",
                "spring.datasource.password=pass",
                "sociomart.auth.admin-password=admin123",
                "sociomart.auth.super-admin-password=super123"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .satisfiesAnyOf(
                        failure -> assertThat(failure).hasStackTraceContaining("SOCIOMART_DB_JDBC_URL is unresolved (literal placeholder found)"),
                        failure -> assertThat(failure).hasStackTraceContaining("Could not resolve placeholder 'SOCIOMART_DB_JDBC_URL'")
                    );
            });
    }
 
    @Test
    void shouldFailWhenJdbcUrlIsMalformed() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "spring.datasource.url=jdbc:mysql://localhost:3306/db",
                "spring.datasource.username=user",
                "spring.datasource.password=pass",
                "sociomart.auth.admin-password=admin123",
                "sociomart.auth.super-admin-password=super123"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("SOCIOMART_DB_JDBC_URL is not a valid PostgreSQL JDBC URL");
            });
    }
 
    @Test
    void shouldFailWhenUsernameOrPasswordIsMissing() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "spring.datasource.url=jdbc:postgresql://localhost:5432/db",
                "spring.datasource.username=",
                "spring.datasource.password=pass",
                "sociomart.auth.admin-password=admin123",
                "sociomart.auth.super-admin-password=super123"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("SOCIOMART_DB_USERNAME is missing or blank");
            });
    }
 
    @Test
    void shouldFailWhenAdminPasswordsAreMissing() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "spring.datasource.url=jdbc:postgresql://localhost:5432/db",
                "spring.datasource.username=user",
                "spring.datasource.password=pass"
                // admin passwords missing
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Refusing to start in production: SOCIOMART_ADMIN_PASSWORD");
            });
    }
 
    @Test
    void shouldNotFailForLocalProfile() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=local",
                "spring.datasource.url=jdbc:h2:mem:testdb"
            )
            .run(context -> {
                assertThat(context).doesNotHaveBean(ProductionStartupValidator.class);
            });
    }
}
