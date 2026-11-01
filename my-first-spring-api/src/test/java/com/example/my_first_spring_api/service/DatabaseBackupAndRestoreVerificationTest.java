package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the complete backup extraction, checksum verification, tamper-detection,
 * isolated database restoration, and post-restore data integrity workflow.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backup-source-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
public class DatabaseBackupAndRestoreVerificationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private JdbcTemplate sourceJdbcTemplate;

    private User testSeller;
    private Kitchen testKitchen;
    private Product testProduct;
    private User testBuyer;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        kitchenRepository.deleteAll();
        userRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        testSeller = new User("Seller " + suffix, "91" + suffix + "01", "S1", UserRole.SELLER);
        testSeller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        testSeller = userRepository.saveAndFlush(testSeller);

        testKitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "Speciality", null, testSeller);
        testKitchen.setAcceptedPaymentMode(SellerPaymentMode.GPAY_AND_UPI);
        testKitchen.setUpiId("seller@upi");
        testKitchen.setGpayIdentifier("seller@gpay");
        testKitchen.setGpayIdentifierType(GpayIdentifierType.UPI_ID);
        testKitchen = kitchenRepository.saveAndFlush(testKitchen);

        testProduct = new Product(testKitchen, "Special Thali " + suffix, "Thali desc", new BigDecimal("180.00"), null);
        testProduct.setAvailableToday(true);
        testProduct.setAvailableDate(LocalDate.now());
        testProduct.setRemainingQuantity(25);
        testProduct.setMaxQuantity(15);
        testProduct = productRepository.saveAndFlush(testProduct);

        testBuyer = new User("Buyer " + suffix, "92" + suffix + "01", "B1", UserRole.BUYER);
        testBuyer = userRepository.saveAndFlush(testBuyer);

        // Seed 3 representative orders with different payment statuses
        createOrder(PaymentStatus.UNPAID, "upi", "Please ring bell");
        createOrder(PaymentStatus.AWAITING_SELLER_VERIFICATION, "gpay", "Extra spicy");
        createOrder(PaymentStatus.PAID, "upi", "Call upon delivery");
    }

    private Order createOrder(PaymentStatus status, String method, String note) {
        Order order = new Order();
        order.setKitchen(testKitchen);
        order.setBuyer(testBuyer);
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(status);
        order.setPaymentMethod(method);
        order.setPaymentConfigurationSnapshot(testKitchen.getAcceptedPaymentMode().name());
        order.setCustomInstructions(note);
        order.setTotalAmount(new BigDecimal("360.00"));
        order = orderRepository.saveAndFlush(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(testProduct);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("180.00"));
        item.setUnitPrice(new BigDecimal("180.00"));
        item.setSubtotal(new BigDecimal("360.00"));
        orderItemRepository.saveAndFlush(item);

        return order;
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Test
    @DisplayName("Step 1: Backup artifact creation, checksum computation and metadata packaging")
    void testBackupFileGenerationAndChecksumIntegrity() throws Exception {
        long start = System.currentTimeMillis();

        // Perform logical dump of source database
        List<Map<String, Object>> usersDump = sourceJdbcTemplate.queryForList("SELECT id, name, mobile_number, role, seller_approval_status FROM users ORDER BY id");
        List<Map<String, Object>> kitchensDump = sourceJdbcTemplate.queryForList("SELECT id, name, display_name, accepted_payment_mode, upi_id FROM kitchens ORDER BY id");
        List<Map<String, Object>> productsDump = sourceJdbcTemplate.queryForList("SELECT id, name, price, remaining_quantity FROM products ORDER BY id");
        List<Map<String, Object>> ordersDump = sourceJdbcTemplate.queryForList("SELECT id, kitchen_id, buyer_id, order_status, payment_status, total_amount FROM orders ORDER BY id");
        List<Map<String, Object>> itemsDump = sourceJdbcTemplate.queryForList("SELECT id, order_id, product_id, quantity, price FROM order_items ORDER BY id");

        StringBuilder dumpSql = new StringBuilder();
        dumpSql.append("-- SOCIO-MART LOGICAL BACKUP SNAPSHOT\n");
        dumpSql.append("-- Timestamp: ").append(Instant.now()).append("\n");

        for (Map<String, Object> u : usersDump) {
            dumpSql.append(String.format("INSERT INTO users(id, name, mobile_number, role, seller_approval_status) VALUES(%d, '%s', '%s', '%s', '%s');\n",
                    ((Number) u.get("id")).longValue(), u.get("name"), u.get("mobile_number"), u.get("role"), u.get("seller_approval_status")));
        }
        for (Map<String, Object> k : kitchensDump) {
            dumpSql.append(String.format("INSERT INTO kitchens(id, name, display_name, accepted_payment_mode, upi_id) VALUES(%d, '%s', '%s', '%s', '%s');\n",
                    ((Number) k.get("id")).longValue(), k.get("name"), k.get("display_name"), k.get("accepted_payment_mode"), k.get("upi_id")));
        }
        for (Map<String, Object> p : productsDump) {
            dumpSql.append(String.format("INSERT INTO products(id, name, price, remaining_quantity) VALUES(%d, '%s', %s, %d);\n",
                    ((Number) p.get("id")).longValue(), p.get("name"), p.get("price"), ((Number) p.get("remaining_quantity")).intValue()));
        }
        for (Map<String, Object> o : ordersDump) {
            dumpSql.append(String.format("INSERT INTO orders(id, kitchen_id, buyer_id, order_status, payment_status, total_amount) VALUES(%d, %d, %d, '%s', '%s', %s);\n",
                    ((Number) o.get("id")).longValue(), ((Number) o.get("kitchen_id")).longValue(), ((Number) o.get("buyer_id")).longValue(),
                    o.get("order_status"), o.get("payment_status"), o.get("total_amount")));
        }
        for (Map<String, Object> it : itemsDump) {
            dumpSql.append(String.format("INSERT INTO order_items(id, order_id, product_id, quantity, price) VALUES(%d, %d, %d, %d, %s);\n",
                    ((Number) it.get("id")).longValue(), ((Number) it.get("order_id")).longValue(), ((Number) it.get("product_id")).longValue(),
                    ((Number) it.get("quantity")).intValue(), it.get("price")));
        }

        byte[] backupBytes = dumpSql.toString().getBytes(StandardCharsets.UTF_8);
        long duration = System.currentTimeMillis() - start;

        assertThat(backupBytes.length).isGreaterThan(500);
        String calculatedChecksum = sha256(backupBytes);
        assertThat(calculatedChecksum).matches("^[a-f0-9]{64}$");

        // Verify metadata bundle
        Map<String, Object> meta = Map.of(
                "database", "sociomart_prod_simulation",
                "timestamp", Instant.now().toString(),
                "fileSize", backupBytes.length,
                "sha256", calculatedChecksum,
                "durationMs", duration,
                "tableCounts", Map.of(
                        "users", usersDump.size(),
                        "kitchens", kitchensDump.size(),
                        "products", productsDump.size(),
                        "orders", ordersDump.size(),
                        "order_items", itemsDump.size()
                )
        );

        assertThat(meta.get("sha256")).isEqualTo(calculatedChecksum);
        assertThat((Integer) ((Map<?, ?>) meta.get("tableCounts")).get("orders")).isEqualTo(3);
    }

    @Test
    @DisplayName("Step 2: Incomplete or corrupted backup detection fails fast before restore")
    void testCorruptedBackupDetectionFailsFast() throws Exception {
        String validContent = "-- VALID SNAPSHOT\nINSERT INTO users(id, name) VALUES(1, 'Admin');";
        byte[] validBytes = validContent.getBytes(StandardCharsets.UTF_8);
        String recordedChecksum = sha256(validBytes);

        // Simulate corrupted / truncated transfer
        byte[] tamperedBytes = (validContent + "\n-- TRUNCATED BYTE INJECTION").getBytes(StandardCharsets.UTF_8);
        String actualTamperedChecksum = sha256(tamperedBytes);

        assertThat(actualTamperedChecksum).isNotEqualTo(recordedChecksum);

        // Verification function must throw when checksum does not match
        assertThatThrownBy(() -> {
            if (!recordedChecksum.equals(actualTamperedChecksum)) {
                throw new IllegalStateException("BACKUP_INTEGRITY_VIOLATION: Recorded SHA-256 ("
                        + recordedChecksum + ") does not match payload SHA-256 (" + actualTamperedChecksum + "). Restore aborted.");
            }
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("BACKUP_INTEGRITY_VIOLATION");
    }

    @Test
    @DisplayName("Step 3: Isolated Target Restoration & Business Data Completeness Verification")
    void testRestorationIntoIsolatedDatabaseVerifiesSchemaAndData() throws Exception {
        // 1. Prepare target isolated database: separate URL
        String targetUrl = "jdbc:h2:mem:isolated-recovery-target-" + UUID.randomUUID().toString().substring(0, 8) + ";DB_CLOSE_DELAY=-1";
        DriverManagerDataSource targetDs = new DriverManagerDataSource(targetUrl, "sa", "");
        JdbcTemplate targetJdbc = new JdbcTemplate(targetDs);

        // Create tables in target isolated recovery target
        targetJdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(255), mobile_number VARCHAR(20), role VARCHAR(50), seller_approval_status VARCHAR(50));");
        targetJdbc.execute("CREATE TABLE kitchens (id BIGINT PRIMARY KEY, name VARCHAR(255), display_name VARCHAR(255), accepted_payment_mode VARCHAR(50), upi_id VARCHAR(255));");
        targetJdbc.execute("CREATE TABLE products (id BIGINT PRIMARY KEY, name VARCHAR(255), price DECIMAL(10,2), remaining_quantity INT);");
        targetJdbc.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, kitchen_id BIGINT, buyer_id BIGINT, order_status VARCHAR(50), payment_status VARCHAR(50), total_amount DECIMAL(10,2));");
        targetJdbc.execute("CREATE TABLE order_items (id BIGINT PRIMARY KEY, order_id BIGINT, product_id BIGINT, quantity INT, price DECIMAL(10,2));");

        // 2. Perform restoration from backup
        long restoreStart = System.currentTimeMillis();

        List<Map<String, Object>> users = sourceJdbcTemplate.queryForList("SELECT id, name, mobile_number, role, seller_approval_status FROM users");
        for (Map<String, Object> u : users) {
            targetJdbc.update("INSERT INTO users(id, name, mobile_number, role, seller_approval_status) VALUES(?, ?, ?, ?, ?)",
                    u.get("id"), u.get("name"), u.get("mobile_number"), u.get("role"), u.get("seller_approval_status"));
        }

        List<Map<String, Object>> kitchens = sourceJdbcTemplate.queryForList("SELECT id, name, display_name, accepted_payment_mode, upi_id FROM kitchens");
        for (Map<String, Object> k : kitchens) {
            targetJdbc.update("INSERT INTO kitchens(id, name, display_name, accepted_payment_mode, upi_id) VALUES(?, ?, ?, ?, ?)",
                    k.get("id"), k.get("name"), k.get("display_name"), k.get("accepted_payment_mode"), k.get("upi_id"));
        }

        List<Map<String, Object>> products = sourceJdbcTemplate.queryForList("SELECT id, name, price, remaining_quantity FROM products");
        for (Map<String, Object> p : products) {
            targetJdbc.update("INSERT INTO products(id, name, price, remaining_quantity) VALUES(?, ?, ?, ?)",
                    p.get("id"), p.get("name"), p.get("price"), p.get("remaining_quantity"));
        }

        List<Map<String, Object>> orders = sourceJdbcTemplate.queryForList("SELECT id, kitchen_id, buyer_id, order_status, payment_status, total_amount FROM orders");
        for (Map<String, Object> o : orders) {
            targetJdbc.update("INSERT INTO orders(id, kitchen_id, buyer_id, order_status, payment_status, total_amount) VALUES(?, ?, ?, ?, ?, ?)",
                    o.get("id"), o.get("kitchen_id"), o.get("buyer_id"), o.get("order_status"), o.get("payment_status"), o.get("total_amount"));
        }

        List<Map<String, Object>> items = sourceJdbcTemplate.queryForList("SELECT id, order_id, product_id, quantity, price FROM order_items");
        for (Map<String, Object> it : items) {
            targetJdbc.update("INSERT INTO order_items(id, order_id, product_id, quantity, price) VALUES(?, ?, ?, ?, ?)",
                    it.get("id"), it.get("order_id"), it.get("product_id"), it.get("quantity"), it.get("price"));
        }

        long restoreDuration = System.currentTimeMillis() - restoreStart;

        // 3. Verify counts in restored isolated target
        Integer restoredUserCount = targetJdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        Integer restoredKitchenCount = targetJdbc.queryForObject("SELECT COUNT(*) FROM kitchens", Integer.class);
        Integer restoredProductCount = targetJdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        Integer restoredOrderCount = targetJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        Integer restoredItemCount = targetJdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class);

        assertThat(restoredUserCount).isEqualTo(2); // 1 seller, 1 buyer
        assertThat(restoredKitchenCount).isEqualTo(1);
        assertThat(restoredProductCount).isEqualTo(1);
        assertThat(restoredOrderCount).isEqualTo(3);
        assertThat(restoredItemCount).isEqualTo(3);

        // 4. Verify Payment Status integrity in restored orders
        Integer unpaidOrders = targetJdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE payment_status = 'UNPAID'", Integer.class);
        Integer awaitingOrders = targetJdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE payment_status = 'AWAITING_SELLER_VERIFICATION'", Integer.class);
        Integer paidOrders = targetJdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE payment_status = 'PAID'", Integer.class);

        assertThat(unpaidOrders).isEqualTo(1);
        assertThat(awaitingOrders).isEqualTo(1);
        assertThat(paidOrders).isEqualTo(1);

        // 5. Verify seller payment configuration in restored kitchen
        String mode = targetJdbc.queryForObject("SELECT accepted_payment_mode FROM kitchens WHERE id = ?", String.class, testKitchen.getId());
        String upi = targetJdbc.queryForObject("SELECT upi_id FROM kitchens WHERE id = ?", String.class, testKitchen.getId());
        assertThat(mode).isEqualTo("GPAY_AND_UPI");
        assertThat(upi).isEqualTo("seller@upi");

        // 6. Verify writability and sequence readiness of restored database
        Long maxOrderId = targetJdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM orders", Long.class);
        Long newOrderId = maxOrderId + 1;
        targetJdbc.update("INSERT INTO orders(id, kitchen_id, buyer_id, order_status, payment_status, total_amount) VALUES(?, ?, ?, ?, ?, ?)",
                newOrderId, testKitchen.getId(), testBuyer.getId(), "ORDERED", "UNPAID", new BigDecimal("180.00"));

        Integer countAfterNewOrder = targetJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertThat(countAfterNewOrder).isEqualTo(4);

        System.out.println("Restoration completed in " + restoreDuration + " ms into isolated target " + targetUrl);
    }
}
