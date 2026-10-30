package com.example.my_first_spring_api.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.my_first_spring_api.SecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "SOCIOMART_DB_JDBC_URL=jdbc:h2:mem:postgres_schema_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "SOCIOMART_DB_USERNAME=sa",
                "SOCIOMART_DB_PASSWORD=",
                "spring.datasource.url=jdbc:h2:mem:postgres_schema_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.defer-datasource-initialization=false",
                "spring.flyway.enabled=true"
        })
@ActiveProfiles("test")
class PostgresSchemaMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Test
    void migrationsCreateSchemaThatMatchesAllJpaEntities() {
        // V4 adds the Spring Session tables; V5 adds hot-path indexes only;
        // V6 adds the password_reset_tokens table; V7 adds users.email;
        // V8 adds the password_reset_requests table; V9 defensively re-adds
        // users.email if V7 was never applied (idempotent, IF NOT EXISTS);
        // V10 hardens product is_available and order status defaults;
        // V11 syncs legacy orders.status column and sets default PENDING;
        // V12 syncs legacy order_items.unit_price column;
        // V14 syncs payment_method column on orders; V15 adds scheduled opening
        // timestamps; V16 adds orderable Quick Post metadata and optional images.
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("16");

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history' "
                        + "AND LOWER(table_name) NOT LIKE 'spring_session%'",
                Integer.class);
        Assertions.assertNotNull(tableCount);
        assertThat(tableCount).isEqualTo(26);

        Integer emailColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'PUBLIC' AND table_name = 'USERS' "
                        + "AND column_name = 'EMAIL'",
                Integer.class);
        Assertions.assertNotNull(emailColumnCount);
        assertThat(emailColumnCount).isEqualTo(1);

        Integer sessionTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE' "
                        + "AND LOWER(table_name) LIKE 'spring_session%'",
                Integer.class);
        Assertions.assertNotNull(sessionTableCount);
        assertThat(sessionTableCount).isEqualTo(2);

        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = 'PUBLIC' AND constraint_type = 'FOREIGN KEY'",
                Integer.class);
        Assertions.assertNotNull(foreignKeyCount);
        assertThat(foreignKeyCount).isGreaterThanOrEqualTo(20);

        Integer onboardingColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'PUBLIC' "
                        + "AND ((table_name = 'USERS' AND column_name IN "
                        + "('PASSWORD_HASH', 'SELLER_WHATSAPP_NUMBER', 'SELLER_ALTERNATE_CONTACT', 'SELLER_CATEGORY')) "
                        + "OR (table_name = 'KITCHENS' AND column_name = 'SPECIALITY'))",
                Integer.class);
        Assertions.assertNotNull(onboardingColumnCount);
        assertThat(onboardingColumnCount).isEqualTo(5);

        Integer productFeatureColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'PUBLIC' "
                        + "AND table_name = 'PRODUCTS' "
                        + "AND column_name IN ('ORDERS_OPEN_FROM', 'QUICK_POST', 'QUICK_POST_IMAGE_DATA')",
                Integer.class);
        Assertions.assertNotNull(productFeatureColumnCount);
        assertThat(productFeatureColumnCount).isEqualTo(3);
    }
}
