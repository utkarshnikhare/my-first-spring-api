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
        "sociomart.demo.direct-auth-enabled=true"
})
@ActiveProfiles("postgres-demo")
@Testcontainers(disabledWithoutDocker = true)
class PostgresRuntimeSmokeTest extends AbstractRuntimeSmokeFlow {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("SOCIOMART_DB_JDBC_URL", POSTGRES::getJdbcUrl);
        registry.add("SOCIOMART_DB_USERNAME", POSTGRES::getUsername);
        registry.add("SOCIOMART_DB_PASSWORD", POSTGRES::getPassword);
        registry.add("sociomart.demo.admin-password", () -> ADMIN_PASSWORD);
        registry.add("sociomart.demo.super-admin-password", () -> SUPER_ADMIN_PASSWORD);
    }
}
