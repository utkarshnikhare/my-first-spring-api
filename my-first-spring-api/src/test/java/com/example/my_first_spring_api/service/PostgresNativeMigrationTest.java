package com.example.my_first_spring_api.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.defer-datasource-initialization=false",
                "spring.flyway.enabled=true"
        })
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresNativeMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // Point the datasource directly at the Testcontainers PostgreSQL. The
        // "test" profile hardcodes an H2 URL and never reads SOCIOMART_DB_JDBC_URL
        // (that name is consumed only by application-prod.properties), so setting
        // the env-var name alone left this test silently running against H2.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void flywayMigrationCreatesSchemaValidatedByHibernateOnPostgres() {
        // V4 adds the Spring Session tables; V5 adds hot-path indexes only;
        // V6 adds the password_reset_tokens table; V7 adds users.email;
        // V8 adds the password_reset_requests table; V9 defensively re-adds
        // users.email if V7 was never applied (idempotent, IF NOT EXISTS);
        // V10 hardens product is_available and order status defaults;
        // V11 syncs legacy orders.status column and sets default PENDING;
        // V12 syncs legacy order_items.unit_price column;
        // V13 syncs legacy order_items.subtotal column; V14 adds payment_method
        // to orders; V15 adds scheduled opening timestamps; V16 adds orderable
        // Quick Post metadata and optional images; V17 repairs missing payment_method.
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("17");

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history' "
                        + "AND LOWER(table_name) NOT LIKE 'spring_session%'",
                Integer.class);
        assertThat(tableCount).isEqualTo(26);

        Integer emailColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'users' "
                        + "AND column_name = 'email'",
                Integer.class);
        assertThat(emailColumnCount).isEqualTo(1);

        Integer paymentMethodColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'orders' "
                        + "AND column_name = 'payment_method'",
                Integer.class);
        assertThat(paymentMethodColumnCount).isEqualTo(1);

        Integer sessionTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND LOWER(table_name) LIKE 'spring_session%'",
                Integer.class);
        assertThat(sessionTableCount).isEqualTo(2);

        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = 'public' AND constraint_type = 'FOREIGN KEY'",
                Integer.class);
        assertThat(foreignKeyCount).isGreaterThanOrEqualTo(20);

        Integer onboardingColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND ((table_name = 'users' AND column_name IN "
                        + "('password_hash', 'seller_whatsapp_number', 'seller_alternate_contact', 'seller_category')) "
                        + "OR (table_name = 'kitchens' AND column_name = 'speciality'))",
                Integer.class);
        assertThat(onboardingColumnCount).isEqualTo(5);

        Integer productFeatureColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'products' "
                        + "AND column_name IN ('orders_open_from', 'quick_post', 'quick_post_image_data')",
                Integer.class);
        assertThat(productFeatureColumnCount).isEqualTo(3);
    }
}
