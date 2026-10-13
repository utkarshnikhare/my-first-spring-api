package com.example.my_first_spring_api.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs REAL application queries against a real PostgreSQL (Flyway schema +
 * Hibernate {@code ddl-auto=validate}) so H2-only behaviour cannot hide a
 * Postgres error.
 *
 * <p>Needs Docker (always present on the CI runner); skipped locally without it.</p>
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.flyway.enabled=true",
        "sociomart.auth.password-login-enabled=true"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresRuntimeSmokeTest extends AbstractRuntimeSmokeFlow {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Point the datasource directly at the Testcontainers PostgreSQL and let
        // Flyway build the schema (see spring.flyway.enabled=true above). The
        // "test" profile hardcodes an H2 URL and never reads SOCIOMART_DB_JDBC_URL,
        // so setting the env-var name alone left this test silently running on H2.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("sociomart.auth.admin-password", () -> ADMIN_PASSWORD);
        registry.add("sociomart.auth.super-admin-password", () -> SUPER_ADMIN_PASSWORD);
    }
}
