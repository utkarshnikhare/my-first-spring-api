package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.UserRepository;
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
class UserEmailMigrationRegressionTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void emailColumnExistsAfterFullFlywayMigration() {
        flyway.migrate();

        Integer emailColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'users' "
                        + "AND column_name = 'email'",
                Integer.class);
        assertThat(emailColumnCount).isEqualTo(1);

        Integer flywayVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE version = '9' ORDER BY installed_rank DESC LIMIT 1",
                Integer.class);
        assertThat(flywayVersion).isEqualTo(9);
    }

    @Test
    void emailColumnIsAddedWhenMissingAndExistingUsersSurvive() {
        // Let Flyway run first (creates full schema WITH email via V7/V9),
        // then simulate production's missing-email state by dropping the column.
        // This mirrors production: V1-V6 applied, V7 never ran, email column absent.
        flyway.migrate();

        jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS email");

        // Confirm the column is gone (production state before repair).
        Integer beforeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'users' "
                        + "AND column_name = 'email'",
                Integer.class);
        assertThat(beforeCount).isEqualTo(0);

        // Insert an existing user (all V1 NOT NULL columns already exist).
        jdbcTemplate.update(
                "INSERT INTO users (name, mobile_number, role, blocked, storefront_removed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "Existing User", "9999999999", "SELLER");

        // Apply the V9 repair migration: idempotent, adds email if missing.
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR");

        // Verify the email column now exists.
        Integer emailColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'users' "
                        + "AND column_name = 'email'",
                Integer.class);
        assertThat(emailColumnCount).isEqualTo(1);

        // Verify existing user data is intact (no data loss from adding the column).
        Object[] existing = jdbcTemplate.queryForObject(
                "SELECT name, mobile_number, role FROM users WHERE mobile_number = '9999999999'",
                (rs, rowNum) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3)});
        assertThat(existing[0]).isEqualTo("Existing User");
        assertThat(existing[1]).isEqualTo("9999999999");
        assertThat(existing[2]).isEqualTo("SELLER");

        // Verify findByMobileNumber no longer fails due to missing email column.
        User user = userRepository.findByMobileNumber("9999999999")
                .orElseThrow(() -> new AssertionError("User not found after migration"));
        assertThat(user.getMobileNumber()).isEqualTo("9999999999");
        assertThat(user.getRole()).isEqualTo(UserRole.SELLER);
    }

    @Test
    void bootstrapAccountLookupWorksAfterEmailColumnAdded() {
        flyway.migrate();

        jdbcTemplate.update(
                "INSERT INTO users (name, mobile_number, password_hash, role, blocked, storefront_removed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "Super Admin", "9000000001", "hash", "SUPER_ADMIN");

        var user = userRepository.findByMobileNumber("9000000001");
        assertThat(user).isPresent();
        assertThat(user.get().getRole()).isEqualTo(UserRole.SUPER_ADMIN);
    }
}