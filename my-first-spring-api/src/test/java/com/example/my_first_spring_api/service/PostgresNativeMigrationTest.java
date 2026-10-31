package com.example.my_first_spring_api.service;

import db.migration.V20__allow_seller_verified_payment_states;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.defer-datasource-initialization=false",
                "spring.main.lazy-initialization=true",
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
        // Quick Post metadata and optional images; V17 repairs missing payment_method;
        // V18 adds the nullable Quick Post retry key and uniqueness constraint;
        // V19 adds seller-configurable payment methods; V20 adds seller-verified
        // payment states without rewriting historical orders; V21 stores an
        // immutable, non-sensitive payment configuration snapshot per placed order.
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("21");

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

        Integer paymentSnapshotColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'orders' "
                        + "AND column_name = 'payment_configuration_snapshot'",
                Integer.class);
        assertThat(paymentSnapshotColumnCount).isEqualTo(1);

        String paymentStatusNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'orders' "
                        + "AND column_name = 'payment_status'",
                String.class);
        assertThat(paymentStatusNullable).isEqualTo("NO");

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
                        + "AND column_name IN ('orders_open_from', 'quick_post', 'quick_post_image_data', "
                        + "'quick_post_request_id')",
                Integer.class);
        assertThat(productFeatureColumnCount).isEqualTo(4);

        Integer sellerPaymentColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'kitchens' "
                        + "AND column_name IN ('accepted_payment_mode', 'gpay_identifier_type', 'gpay_identifier')",
                Integer.class);
        assertThat(sellerPaymentColumnCount).isEqualTo(3);

        Integer paymentStatusCheckCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint "
                        + "WHERE conrelid = 'orders'::regclass AND contype = 'c' "
                        + "AND pg_get_constraintdef(oid) LIKE '%payment_status%'",
                Integer.class);
        assertThat(paymentStatusCheckCount).isEqualTo(1);

        String paymentStatusConstraint = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conrelid = 'orders'::regclass AND contype = 'c' "
                        + "AND pg_get_constraintdef(oid) LIKE '%payment_status%'",
                String.class);
        assertThat(paymentStatusConstraint).contains(
                "PENDING", "UNPAID", "AWAITING_SELLER_VERIFICATION", "PAID", "WILL_PAY_LATER");

        jdbcTemplate.execute("CREATE TEMP TABLE payment_status_constraint_probe "
                + "(LIKE orders INCLUDING CONSTRAINTS)");
        long probeId = 1L;
        for (String validStatus : new String[]{
                "PENDING", "UNPAID", "AWAITING_SELLER_VERIFICATION", "PAID", "WILL_PAY_LATER"
        }) {
            jdbcTemplate.update(
                    "INSERT INTO payment_status_constraint_probe "
                            + "(id, buyer_id, kitchen_id, order_number, order_status, payment_status) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    probeId++, 1L, 1L, "status-probe-" + validStatus, "ORDERED", validStatus);
        }
        long invalidProbeId = probeId;
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO payment_status_constraint_probe "
                        + "(id, buyer_id, kitchen_id, order_number, order_status, payment_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                invalidProbeId, 1L, 1L, "status-probe-invalid", "ORDERED", "NOT_A_PAYMENT_STATUS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v20OnlyDropsPaymentStatusChecksOwnedByOrders() throws Exception {
        String schema = "v20_constraint_probe_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            connection.setSchema(schema);
            statement.execute("""
                    CREATE TABLE orders (
                        payment_status text NOT NULL,
                        quantity integer NOT NULL,
                        CONSTRAINT orders_payment_status_check
                            CHECK (payment_status IN ('PENDING', 'PAID', 'WILL_PAY_LATER')),
                        CONSTRAINT shared_check CHECK (quantity >= 0)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE unrelated (
                        payment_status text NOT NULL,
                        CONSTRAINT shared_check
                            CHECK (payment_status IN ('PENDING', 'PAID', 'WILL_PAY_LATER'))
                    )
                    """);

            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V20__allow_seller_verified_payment_states().migrate(context);

            try (ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) FROM pg_constraint "
                            + "WHERE conrelid = 'orders'::regclass AND conname = 'shared_check'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO orders (payment_status, quantity) VALUES ('PAID', -1)"))
                    .isInstanceOf(java.sql.SQLException.class);
        } finally {
            try (Connection cleanup = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = cleanup.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test
    void v20ConstraintOwnershipRegressionFixtureWithSameNamedConstraintAndRecords() throws Exception {
        String schema = "v20_fixture_" + UUID.randomUUID().toString().replace("-", "");
        String sharedConstraintName = "orders_payment_status_check";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            connection.setSchema(schema);

            // 1. orders table with legacy CHECK constraint
            statement.execute(String.format("""
                    CREATE TABLE orders (
                        id bigint PRIMARY KEY,
                        order_number text NOT NULL,
                        payment_status text NOT NULL,
                        quantity integer NOT NULL,
                        CONSTRAINT %s
                            CHECK (payment_status IN ('PENDING', 'PAID', 'WILL_PAY_LATER')),
                        CONSTRAINT orders_quantity_positive CHECK (quantity > 0)
                    )
                    """, sharedConstraintName));

            // 2. Another table with a CHECK constraint having the EXACT SAME constraint name
            statement.execute(String.format("""
                    CREATE TABLE audit_log (
                        id bigint PRIMARY KEY,
                        target_entity text NOT NULL,
                        lifecycle_stage text NOT NULL,
                        CONSTRAINT %s
                            CHECK (lifecycle_stage IN ('INGESTED', 'PROCESSED', 'ARCHIVED'))
                    )
                    """, sharedConstraintName));

            // 3. Representative order records and unrelated-table records
            statement.execute("""
                    INSERT INTO orders (id, order_number, payment_status, quantity) VALUES
                    (1, 'ORD-001', 'PENDING', 2),
                    (2, 'ORD-002', 'PAID', 1),
                    (3, 'ORD-003', 'WILL_PAY_LATER', 4)
                    """);

            statement.execute("""
                    INSERT INTO audit_log (id, target_entity, lifecycle_stage) VALUES
                    (10, 'orders', 'INGESTED'),
                    (20, 'kitchens', 'PROCESSED')
                    """);

            // Run V20
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V20__allow_seller_verified_payment_states().migrate(context);

            // Assert: The intended obsolete constraint on orders is handled correctly
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT con.conname, pg_get_constraintdef(con.oid)
                    FROM pg_constraint con
                    JOIN pg_class tbl ON tbl.oid = con.conrelid
                    JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
                    WHERE LOWER(tbl.relname) = 'orders'
                      AND LOWER(ns.nspname) = LOWER(?)
                      AND con.contype = 'c'
                      AND LOWER(pg_get_constraintdef(con.oid)) LIKE '%payment_status%'
                    """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("conname")).isEqualTo("orders_payment_status_check");
                    String def = rs.getString(2);
                    assertThat(def).contains("PENDING", "UNPAID", "AWAITING_SELLER_VERIFICATION", "PAID", "WILL_PAY_LATER");
                    assertThat(rs.next()).isFalse();
                }
            }

            // Assert: The unrelated same-named CHECK constraint survives on the unrelated table
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT con.conname, pg_get_constraintdef(con.oid)
                    FROM pg_constraint con
                    JOIN pg_class tbl ON tbl.oid = con.conrelid
                    JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
                    WHERE LOWER(tbl.relname) = 'audit_log'
                      AND LOWER(ns.nspname) = LOWER(?)
                      AND con.contype = 'c'
                    """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("conname")).isEqualTo(sharedConstraintName);
                    assertThat(rs.getString(2)).contains("INGESTED", "PROCESSED", "ARCHIVED");
                    assertThat(rs.next()).isFalse();
                }
            }

            // Assert: Unrelated table's data remains unchanged
            try (ResultSet rs = statement.executeQuery("SELECT id, target_entity, lifecycle_stage FROM audit_log ORDER BY id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(10L);
                assertThat(rs.getString("target_entity")).isEqualTo("orders");
                assertThat(rs.getString("lifecycle_stage")).isEqualTo("INGESTED");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(20L);
                assertThat(rs.getString("target_entity")).isEqualTo("kitchens");
                assertThat(rs.getString("lifecycle_stage")).isEqualTo("PROCESSED");

                assertThat(rs.next()).isFalse();
            }

            // Assert: Existing orders remain unchanged
            try (ResultSet rs = statement.executeQuery("SELECT id, order_number, payment_status, quantity FROM orders ORDER BY id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(1L);
                assertThat(rs.getString("order_number")).isEqualTo("ORD-001");
                assertThat(rs.getString("payment_status")).isEqualTo("PENDING");
                assertThat(rs.getInt("quantity")).isEqualTo(2);

                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(2L);
                assertThat(rs.getString("order_number")).isEqualTo("ORD-002");
                assertThat(rs.getString("payment_status")).isEqualTo("PAID");
                assertThat(rs.getInt("quantity")).isEqualTo(1);

                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(3L);
                assertThat(rs.getString("order_number")).isEqualTo("ORD-003");
                assertThat(rs.getString("payment_status")).isEqualTo("WILL_PAY_LATER");
                assertThat(rs.getInt("quantity")).isEqualTo(4);

                assertThat(rs.next()).isFalse();
            }

            // Assert: Expected payment constraints exist after migration (UNPAID and AWAITING_SELLER_VERIFICATION accepted)
            statement.executeUpdate("""
                    INSERT INTO orders (id, order_number, payment_status, quantity) VALUES
                    (4, 'ORD-004', 'UNPAID', 1),
                    (5, 'ORD-005', 'AWAITING_SELLER_VERIFICATION', 3)
                    """);

            // Invalid payment status on orders is rejected
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO orders (id, order_number, payment_status, quantity) VALUES (6, 'ORD-006', 'INVALID_PAYMENT_STATUS', 1)"))
                    .isInstanceOf(java.sql.SQLException.class);

            // Unrelated table still enforces its own constraint
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO audit_log (id, target_entity, lifecycle_stage) VALUES (30, 'orders', 'INVALID_STAGE')"))
                    .isInstanceOf(java.sql.SQLException.class);

            // Valid insert into unrelated table works
            statement.executeUpdate(
                    "INSERT INTO audit_log (id, target_entity, lifecycle_stage) VALUES (30, 'orders', 'ARCHIVED')");

            // Assert: Idempotent migration - running V20 again in supported migration workflow causes no side effects
            new V20__allow_seller_verified_payment_states().migrate(context);

            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM orders")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(5);
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM audit_log")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
        } finally {
            try (Connection cleanup = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = cleanup.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
