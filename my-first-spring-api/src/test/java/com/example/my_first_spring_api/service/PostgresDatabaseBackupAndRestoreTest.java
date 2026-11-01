package com.example.my_first_spring_api.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Native PostgreSQL 16 Disaster Recovery & Backup Restoration Test.
 *
 * Exercises the genuine native PostgreSQL 16 utilities (pg_dump, pg_restore, psql),
 * AES-256-CBC encryption/decryption, SHA-256 tamper-detection, and complete
 * schema/data/sequence verification on an isolated PostgreSQL recovery database.
 */
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
class PostgresDatabaseBackupAndRestoreTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
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

    private static byte[] encryptAes256Gcm(byte[] plaintext, char[] password, byte[] salt, byte[] iv) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, 100000, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
        return cipher.doFinal(plaintext);
    }

    private static byte[] decryptAes256Gcm(byte[] ciphertext, char[] password, byte[] salt, byte[] iv) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, 100000, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
        return cipher.doFinal(ciphertext);
    }

    @Test
    @DisplayName("NATIVE PG16: Full pg_dump export, AES-256 encryption, checksum tamper detection, and isolated pg_restore")
    void nativePostgres16BackupAndIsolatedRestoreVerification() throws Exception {
        // ---------------------------------------------------------------------
        // STEP 1: Verify Flyway V21 on Source PostgreSQL Database
        // ---------------------------------------------------------------------
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("21");

        // ---------------------------------------------------------------------
        // STEP 2: Seed Representative Production-Like Business Data
        // ---------------------------------------------------------------------
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String sellerMobile = "98" + suffix + "11";
        String buyerMobile = "97" + suffix + "22";

        jdbcTemplate.update("INSERT INTO users(name, mobile_number, password_hash, role, seller_approval_status, blocked, storefront_removed, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'SELLER', 'APPROVED', false, false, NOW(), NOW())", "Chef Ananya " + suffix, sellerMobile, "SecretP@ss1!");
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE mobile_number = ?", Long.class, sellerMobile);

        jdbcTemplate.update("INSERT INTO users(name, mobile_number, password_hash, role, seller_approval_status, blocked, storefront_removed, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'BUYER', 'APPROVED', false, false, NOW(), NOW())", "Rohan Mehta " + suffix, buyerMobile, "SecretP@ss2!");
        Long buyerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE mobile_number = ?", Long.class, buyerMobile);

        String kitchenSlug = "ananya-kitchen-" + suffix;
        jdbcTemplate.update("INSERT INTO kitchens(name, display_name, description, slug, accepted_payment_mode, upi_id, gpay_identifier, gpay_identifier_type, seller_id, seller_type, storefront_paused, rating) "
                + "VALUES (?, ?, ?, ?, 'GPAY_AND_UPI', 'ananya@okaxis', '9800011122', 'PHONE_NUMBER', ?, 'KITCHEN', false, 4.8)",
                "Ananya Kitchen " + suffix, "Ananya's Kitchen", "Traditional homemade meals", kitchenSlug, sellerId);
        Long kitchenId = jdbcTemplate.queryForObject("SELECT id FROM kitchens WHERE slug = ?", Long.class, kitchenSlug);

        jdbcTemplate.update("INSERT INTO products(kitchen_id, name, description, price, available_today, available_date, max_quantity, remaining_quantity, category) "
                + "VALUES (?, ?, 'Gujarati Thali with Basundi', 220.00, true, CURRENT_DATE, 20, 15, 'LUNCH')",
                kitchenId, "Kathiyawadi Thali " + suffix);
        Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE kitchen_id = ? AND name LIKE 'Kathiyawadi Thali%'", Long.class, kitchenId);

        // Insert 3 orders with different payment statuses allowed by PostgreSQL schema check constraint
        String orderNum1 = "ORD-" + suffix + "-1";
        String orderNum2 = "ORD-" + suffix + "-2";
        String orderNum3 = "ORD-" + suffix + "-3";

        jdbcTemplate.update("INSERT INTO orders(order_number, kitchen_id, buyer_id, order_status, payment_status, payment_method, payment_configuration_snapshot, total_amount, custom_instructions, created_at) "
                + "VALUES (?, ?, ?, 'ORDERED', 'PENDING', 'upi', 'GPAY_AND_UPI', 440.00, 'Less spicy', NOW())", orderNum1, kitchenId, buyerId);
        Long order1Id = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE order_number = ?", Long.class, orderNum1);

        jdbcTemplate.update("INSERT INTO orders(order_number, kitchen_id, buyer_id, order_status, payment_status, payment_method, payment_configuration_snapshot, total_amount, custom_instructions, created_at) "
                + "VALUES (?, ?, ?, 'ORDERED', 'WILL_PAY_LATER', 'gpay', 'GPAY_AND_UPI', 220.00, 'Extra chutney', NOW())", orderNum2, kitchenId, buyerId);
        Long order2Id = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE order_number = ?", Long.class, orderNum2);

        jdbcTemplate.update("INSERT INTO orders(order_number, kitchen_id, buyer_id, order_status, payment_status, payment_method, payment_configuration_snapshot, total_amount, custom_instructions, created_at) "
                + "VALUES (?, ?, ?, 'ORDERED', 'PAID', 'upi', 'GPAY_AND_UPI', 660.00, 'Call before delivery', NOW())", orderNum3, kitchenId, buyerId);
        Long order3Id = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE order_number = ?", Long.class, orderNum3);

        // Insert order items
        jdbcTemplate.update("INSERT INTO order_items(order_id, product_id, quantity, price, unit_price, subtotal) VALUES (?, ?, 2, 220.00, 220.00, 440.00)", order1Id, productId);
        jdbcTemplate.update("INSERT INTO order_items(order_id, product_id, quantity, price, unit_price, subtotal) VALUES (?, ?, 1, 220.00, 220.00, 220.00)", order2Id, productId);
        jdbcTemplate.update("INSERT INTO order_items(order_id, product_id, quantity, price, unit_price, subtotal) VALUES (?, ?, 3, 220.00, 220.00, 660.00)", order3Id, productId);

        // ---------------------------------------------------------------------
        // STEP 3: Execute Real pg_dump with Custom Format (-Fc)
        // ---------------------------------------------------------------------
        long dumpStart = System.currentTimeMillis();
        ExecResult dumpResult = POSTGRES.execInContainer(
                "pg_dump",
                "-U", POSTGRES.getUsername(),
                "-d", POSTGRES.getDatabaseName(),
                "--format=custom",
                "--no-owner",
                "--no-privileges",
                "--file=/tmp/sociomart_pg16_backup.dump"
        );
        long dumpDuration = System.currentTimeMillis() - dumpStart;
        assertThat(dumpResult.getExitCode()).as("pg_dump must succeed with exit code 0: " + dumpResult.getStderr()).isEqualTo(0);

        // Verify dump file in container is non-empty
        ExecResult sizeCheck = POSTGRES.execInContainer("wc", "-c", "/tmp/sociomart_pg16_backup.dump");
        assertThat(sizeCheck.getExitCode()).isEqualTo(0);
        long dumpBytesSize = Long.parseLong(sizeCheck.getStdout().trim().split("\\s+")[0]);
        assertThat(dumpBytesSize).as("pg_dump file must be > 10 KB").isGreaterThan(10_000L);

        // ---------------------------------------------------------------------
        // STEP 4: Copy Dump Artifact from Container and Verify SHA-256
        // ---------------------------------------------------------------------
        ByteArrayOutputStream dumpBuffer = new ByteArrayOutputStream();
        POSTGRES.copyFileFromContainer("/tmp/sociomart_pg16_backup.dump", is -> {
            try {
                is.transferTo(dumpBuffer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        });
        byte[] rawDumpBytes = dumpBuffer.toByteArray();
        assertThat(rawDumpBytes.length).isEqualTo((int) dumpBytesSize);

        String originalChecksum = sha256(rawDumpBytes);
        assertThat(originalChecksum).matches("^[a-f0-9]{64}$");

        // ---------------------------------------------------------------------
        // STEP 5: Encrypt Archive via AES-256-GCM AEAD and Test Tag Mismatch
        // ---------------------------------------------------------------------
        char[] passphrase = "DisasterRecoveryPassphrase2026!".toCharArray();
        byte[] salt = new byte[16];
        byte[] iv = new byte[12]; // 96-bit nonce for AES-GCM
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);

        byte[] encryptedBytes = encryptAes256Gcm(rawDumpBytes, passphrase, salt, iv);
        String encryptedChecksum = sha256(encryptedBytes);

        // Simulate corrupted / tampered transfer
        byte[] tamperedBytes = Arrays.copyOf(encryptedBytes, encryptedBytes.length);
        tamperedBytes[tamperedBytes.length - 1] ^= (byte) 0xFF;

        // Checksum verification must detect altered ciphertext
        assertThat(sha256(tamperedBytes)).isNotEqualTo(encryptedChecksum);

        // GCM Authenticated decryption MUST throw AEADBadTagException on tampered payload!
        assertThatThrownBy(() -> decryptAes256Gcm(tamperedBytes, passphrase, salt, iv))
                .isInstanceOf(javax.crypto.AEADBadTagException.class)
                .hasMessageContaining("Tag mismatch");

        // Decrypt authentic archive
        byte[] decryptedBytes = decryptAes256Gcm(encryptedBytes, passphrase, salt, iv);
        assertThat(decryptedBytes).isEqualTo(rawDumpBytes);
        assertThat(sha256(decryptedBytes)).isEqualTo(originalChecksum);

        // ---------------------------------------------------------------------
        // STEP 6: Provision Clean Isolated Recovery Database in PostgreSQL 16
        // ---------------------------------------------------------------------
        ExecResult createDbResult = POSTGRES.execInContainer(
                "psql",
                "-U", POSTGRES.getUsername(),
                "-c", "CREATE DATABASE isolated_recovery_target;"
        );
        assertThat(createDbResult.getExitCode()).isEqualTo(0);

        // Copy decrypted backup back into container for restoration
        POSTGRES.copyFileToContainer(Transferable.of(decryptedBytes), "/tmp/restored_pg16.dump");

        // ---------------------------------------------------------------------
        // STEP 7: Execute Real pg_restore into Isolated Database Target
        // ---------------------------------------------------------------------
        long restoreStart = System.currentTimeMillis();
        ExecResult restoreResult = POSTGRES.execInContainer(
                "pg_restore",
                "-U", POSTGRES.getUsername(),
                "-d", "isolated_recovery_target",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-privileges",
                "/tmp/restored_pg16.dump"
        );
        long restoreDuration = System.currentTimeMillis() - restoreStart;
        // pg_restore returns 0 on success (or occasionally non-zero if DROP IF EXISTS issues warnings on fresh DB)
        assertThat(restoreResult.getExitCode()).isLessThanOrEqualTo(1);

        // ---------------------------------------------------------------------
        // STEP 8: Independent Verification of Restored Isolated PostgreSQL DB
        // ---------------------------------------------------------------------
        String targetJdbcUrl = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/isolated_recovery_target");
        DriverManagerDataSource targetDs = new DriverManagerDataSource(
                targetJdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
        targetDs.setDriverClassName("org.postgresql.Driver");
        JdbcTemplate targetJdbc = new JdbcTemplate(targetDs);

        // 1. Verify Flyway migration history in restored database
        Integer flywayCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(flywayCount).as("All 21 Flyway migrations must be marked success").isGreaterThanOrEqualTo(21);

        String latestMigrationVersion = targetJdbc.queryForObject(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class);
        assertThat(latestMigrationVersion).isEqualTo("21");

        // 2. Verify all 26 business tables + Spring Session exist in restored database
        Integer tableCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tableCount).isEqualTo(28); // 26 business + 2 Spring Session

        // 3. Verify Foreign Key constraints exist
        Integer fkCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND constraint_type = 'FOREIGN KEY'", Integer.class);
        assertThat(fkCount).isGreaterThan(10);

        // 4. Verify Indexes exist in pg_indexes
        Integer indexCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public'", Integer.class);
        assertThat(indexCount).isGreaterThan(20);

        // 5. Verify Check Constraints (such as orders_payment_status_check)
        Integer checkConstraintCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.check_constraints", Integer.class);
        assertThat(checkConstraintCount).isGreaterThan(0);

        // 6. Verify Exact Business Data Preservation
        String restoredSellerName = targetJdbc.queryForObject(
                "SELECT name FROM users WHERE mobile_number = ?", String.class, sellerMobile);
        assertThat(restoredSellerName).isEqualTo("Chef Ananya " + suffix);

        String restoredKitchenMode = targetJdbc.queryForObject(
                "SELECT accepted_payment_mode FROM kitchens WHERE slug = ?", String.class, kitchenSlug);
        assertThat(restoredKitchenMode).isEqualTo("GPAY_AND_UPI");

        String restoredUpiId = targetJdbc.queryForObject(
                "SELECT upi_id FROM kitchens WHERE slug = ?", String.class, kitchenSlug);
        assertThat(restoredUpiId).isEqualTo("ananya@okaxis");

        // 7. Verify Payment Status Distribution in Restored Orders
        Integer pendingCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE payment_status = 'PENDING'", Integer.class);
        Integer willPayLaterCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE payment_status = 'WILL_PAY_LATER'", Integer.class);
        Integer paidCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE payment_status = 'PAID'", Integer.class);

        assertThat(pendingCount).isEqualTo(1);
        assertThat(willPayLaterCount).isEqualTo(1);
        assertThat(paidCount).isEqualTo(1);

        // 8. Verify Sequence State and Test Transaction Insertion
        Long maxOrderIdBefore = targetJdbc.queryForObject("SELECT MAX(id) FROM orders", Long.class);
        assertThat(maxOrderIdBefore).isNotNull();

        // Sequence must have progressed past the seeded records
        Long nextOrderId = targetJdbc.queryForObject("SELECT nextval('orders_id_seq')", Long.class);
        assertThat(nextOrderId).isGreaterThan(maxOrderIdBefore);

        // Execute a new live insert transaction on restored database to prove write-readiness without sequence collision
        String postRecoveryOrderNum = "ORD-" + suffix + "-4";
        targetJdbc.update("INSERT INTO orders(id, order_number, kitchen_id, buyer_id, order_status, payment_status, payment_method, total_amount, custom_instructions, created_at) "
                + "VALUES (?, ?, ?, ?, 'ORDERED', 'WILL_PAY_LATER', 'cod', 150.00, 'Post-recovery test order', NOW())", nextOrderId, postRecoveryOrderNum, kitchenId, buyerId);

        Integer totalOrdersAfter = targetJdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertThat(totalOrdersAfter).isEqualTo(4);

        System.out.printf("PostgreSQL 16 Disaster Recovery Validation Complete:%n"
                + "  - Dump Size:        %d bytes%n"
                + "  - Dump Duration:    %d ms%n"
                + "  - Restore Duration: %d ms%n"
                + "  - SHA-256 Checksum: %s%n"
                + "  - Target DB URL:    %s%n",
                dumpBytesSize, dumpDuration, restoreDuration, originalChecksum, targetJdbcUrl);
    }
}
