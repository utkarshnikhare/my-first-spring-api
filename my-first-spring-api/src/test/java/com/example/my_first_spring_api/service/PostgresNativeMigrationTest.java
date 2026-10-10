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
                "spring.jpa.defer-datasource-initialization=false"
        })
@ActiveProfiles("postgres-demo")
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
        registry.add("SOCIOMART_DB_JDBC_URL", POSTGRES::getJdbcUrl);
        registry.add("SOCIOMART_DB_USERNAME", POSTGRES::getUsername);
        registry.add("SOCIOMART_DB_PASSWORD", POSTGRES::getPassword);
    }

    @Test
    void flywayMigrationCreatesSchemaValidatedByHibernateOnPostgres() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tableCount).isEqualTo(23);

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
    }
}
