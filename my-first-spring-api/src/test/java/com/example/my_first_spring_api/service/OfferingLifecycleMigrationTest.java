package com.example.my_first_spring_api.service;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OfferingLifecycleMigrationTest {

    @Test
    void legacyProductAndOrderRemainIntactWithoutInferringPublicationState() {
        try (HikariDataSource source = new HikariDataSource()) {
            source.setJdbcUrl("jdbc:h2:mem:lifecycle_" + UUID.randomUUID().toString().replace("-", "")
                    + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            source.setUsername("sa");
            verifyUpgrade(source);
        }
    }

    static void verifyUpgrade(DataSource source) {
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("22")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO users(id, name, role, blocked, storefront_removed) VALUES (1, 'Seller', 'SELLER', false, false)");
        jdbc.update("INSERT INTO users(id, name, role, blocked, storefront_removed) VALUES (2, 'Buyer', 'BUYER', false, false)");
        jdbc.update("INSERT INTO kitchens(id, name, display_name, slug, seller_id, seller_type, storefront_paused) "
                + "VALUES (1, 'legacy', 'Legacy', 'legacy', 1, 'HOMEMADE_PRODUCTS', false)");
        jdbc.update("INSERT INTO products(id, kitchen_id, name, price, price_unit, available_today, remaining_quantity) "
                + "VALUES (1, 1, 'Legacy item', 12.50, 'box', true, 3)");
        jdbc.update("INSERT INTO products(id, kitchen_id, name, price, available_today, available_date, is_preorder) "
                + "VALUES (2, 1, 'Legacy future', 20.00, false, CURRENT_DATE + 10, true)");
        jdbc.update("INSERT INTO products(id, kitchen_id, name, price, available_today, remaining_quantity) "
                + "VALUES (3, 1, 'Legacy unavailable', 30.00, false, 0)");
        jdbc.update("INSERT INTO orders(id, order_number, kitchen_id, buyer_id, order_status, payment_status) "
                + "VALUES (1, 'legacy-order', 1, 2, 'ORDERED', 'PAID')");
        jdbc.update("INSERT INTO order_items(id, order_id, product_id, quantity, price, unit_price, subtotal) "
                + "VALUES (1, 1, 1, 2, 12.50, 12.50, 25.00)");

        Flyway upgraded = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        upgraded.migrate();

        assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("27");
        var product = jdbc.queryForMap("SELECT * FROM products WHERE id = 1");
        assertThat(product.get("NAME")).isEqualTo("Legacy item");
        assertThat((BigDecimal) product.get("PRICE")).isEqualByComparingTo("12.50");
        assertThat(product.get("PRICE_UNIT")).isEqualTo("box");
        assertThat(product.get("AVAILABLE_TODAY")).isEqualTo(true);
        assertThat(product.get("REMAINING_QUANTITY")).isEqualTo(3);
        assertThat(product.get("ARCHIVED")).isEqualTo(false);
        assertThat(product.get("SOLD_OUT_MANUALLY")).isEqualTo(false);
        assertThat(product.get("MANUAL_REACTIVATION_REQUIRED")).isEqualTo(false);
        assertThat(product.get("PUBLICATION_DRAFT")).isNull();
        assertThat(product.get("HOMEMADE_AVAILABILITY_TYPE")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id IN (2, 3) "
                + "AND publication_draft IS NULL AND homemade_availability_type IS NULL AND archived = false",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT is_preorder FROM products WHERE id = 2", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM products WHERE id = 3", Integer.class)).isZero();
        var item = jdbc.queryForMap("SELECT * FROM order_items WHERE id = 1");
        assertThat(((Number) item.get("ORDER_ID")).longValue()).isEqualTo(1L);
        assertThat(((Number) item.get("PRODUCT_ID")).longValue()).isEqualTo(1L);
        assertThat(item.get("QUANTITY")).isEqualTo(2);
        assertThat((BigDecimal) item.get("PRICE")).isEqualByComparingTo("12.50");
        assertThat(item.get("PRODUCT_NAME_SNAPSHOT")).isNull();
        assertThat(item.get("PRICE_UNIT_SNAPSHOT")).isNull();
    }
}
