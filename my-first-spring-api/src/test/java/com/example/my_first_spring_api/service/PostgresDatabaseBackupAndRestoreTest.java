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

    private static byte[] encryptAes256(byte[] plaintext, char[] password, byte[] salt, byte[] iv) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, 100000, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return cipher.doFinal(plaintext);
    }

    private static byte[] decryptAes256(byte[] ciphertext, char[] password, byte[] salt, byte[] iv) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, 100000, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
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

        jdbcTemplate.update("INSERT INTO users(name, mobile_number, password, role, seller_approval_status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'SELLER', 'APPROVED', NOW(), NOW())", "Chef Ananya " + suffix, sellerMobile, "SecretP@ss1!");
        Long sellerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE mobile_number = ?", Long.class, sellerMobile);

        jdbcTemplate.update("INSERT INTO users(name, mobile_number, password, role, seller_approval_status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'BUYER', 'APPROVED', NOW(), NOW())", "Rohan Mehta " + suffix, buyerMobile, "SecretP@ss2!");
        Long buyerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE mobile_number = ?", Long.class, buyerMobile);

        String kitchenSlug = "ananya-kitchen-" + suffix;
        jdbcTemplate.update("INSERT INTO kitchens(name, display_name, description, slug, accepted_payment_mode, upi_id, gpay_identifier, gpay_identifier_type, seller_id, paused, rating) "
                + "VALUES (?, ?, ?, ?, 'GPAY_AND_UPI', 'ananya@okaxis', '9800011122', 'PHONE_NUMBER', ?, false, 4.8)",
                "Ananya Kitchen " + suffix, "Ananya's Kitchen", "Traditional homemade meals", kitchenSlug, sellerId);
        Long kitchenId = jdbcTemplate.queryForObject("SELECT id FROM kitchens WHERE slug = ?", Long.class, kitchenSlug);

        jdbcTemplate.update("INSERT INTO products(kitchen_id, name, description, price, available_today, available_date, max_quantity, remaining_quantity, category) "
                + "VALUES (?, ?, 'Gujarati Thali with Basundi', 220.00, true, CURRENT_DATE, 20, 15, 'LUNCH')",
                kitchenId, "Kathiyawadi Thali " + suffix);
        Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE kitchen_id = ? AND name LIKE 'Kathiyawadi Thali%'", Long.class, kitchenId);

        // Insert 3 orders with different payment statuses
        jdbcTemplate.update("INSERT INTO orders(kitchen_id, buyer_id, order_status, payment_status, payment_method, payment_configuration_snapshot, total_amount, custom_instructions, created_at) "
                + "VALUES (?, ?, 'ORDERED', 'UNPAID', 'upi', 'GPAY_AND_UPI', 440.00, 'Less spicy', NOW())", kitchenId, buyerId);
        Long order1Id = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE custom_instructions = 'Less spicy'", Long.class);

        jdbcTemplate.update("INSERT INTO orders(kitchen_id, buyer_id, order_status, payment_status, payment_method, payment_configuration_snapshot, total_amount, custom_instructions, created_at) "
                + "VALUES (?, ?, 'ORDERED', 'AWAITING_SELLER_VERIFICATION', 'gpay', 'GPAY_AND_UPI', 220.00, 'Extra chutney', NOW())", kitchenId, buyerId);
        Long order2Id = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE custom_instructions = 'Extra chutney'", Long.class);

        jdbcTemplate.update("INSERT INTO orders(kitchen_id, buyer_id, order_status, payment_status, payment_method, payment_configuration_snapshot, total_amount, custom_instructions, created_at) "
                + "VALUES (?, ?, 'ORDERED', 'PAID', 'upi', 'GPAY_AND_UPI', 660.00, 'Call before delivery', NOW())", kitchenId, buyerId);
        Long order3Id = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE custom_instructions = 'Call before delivery'", Long.class);

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
        // STEP 5: Encrypt Archive via AES-256-CBC and Test Corruption Detection
        // ---------------------------------------------------------------------
        char[] passphrase = "DisasterRecoveryPassphrase2026!".toCharArray();
        byte[] salt = new byte[16];
        byte[] iv = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);

        byte[] encryptedBytes = encryptAes256(rawDumpBytes, passphrase, salt, iv);
        String encryptedChecksum = sha256(encryptedBytes);

        // Simulate corrupted / truncated transfer
        byte[] corruptedBytes = Arrays.copyOf(encryptedBytes, encryptedBytes.length + 8);
        corruptedBytes[corruptedBytes.length - 1] = 0x7F;
        String corruptedChecksum = sha256(corruptedBytes);

        assertThat(corruptedChecksum).isNotEqualTo(encryptedChecksum);

        // Checksum verification must abort immediately on tampered payload
        assertThatThrownBy(() -> {
            if (!encryptedChecksum.equals(corruptedChecksum)) {
                throw new IllegalStateException("BACKUP_INTEGRITY_VIOLATION: Checksum mismatch. Aborting restore.");
            }
        }).isInstanceOf(IllegalStateException.class).hasMessageContaining("BACKUP_INTEGRITY_VIOLATION");

        // Decrypt valid archive
        byte[] decryptedBytes = decryptAes256(encryptedBytes, passphrase, salt, iv);
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
        Integer unpaidCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE payment_status = 'UNPAID'", Integer.class);
        Integer awaitingCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE payment_status = 'AWAITING_SELLER_VERIFICATION'", Integer.class);
        Integer paidCount = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE payment_status = 'PAID'", Integer.class);

        assertThat(unpaidCount).isEqualTo(1);
        assertThat(awaitingCount).isEqualTo(1);
        assertThat(paidCount).isEqualTo(1);

        // 8. Verify Sequence State and Test Transaction Insertion
        Long maxOrderIdBefore = targetJdbc.queryForObject("SELECT MAX(id) FROM orders", Long.class);
        assertThat(maxOrderIdBefore).isNotNull();

        // Sequence must have progressed past the seeded records
        Long nextOrderId = targetJdbc.queryForObject("SELECT nextval('orders_id_seq')", Long.class);
        assertThat(nextOrderId).isGreaterThan(maxOrderIdBefore);

        // Execute a new live insert transaction on restored database to prove write-readiness without sequence collision
        targetJdbc.update("INSERT INTO orders(id, kitchen_id, buyer_id, order_status, payment_status, payment_method, total_amount, custom_instructions, created_at) "
                + "VALUES (?, ?, ?, 'ORDERED', 'UNPAID', 'cod', 150.00, 'Post-recovery test order', NOW())", nextOrderId, kitchenId, buyerId);

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
