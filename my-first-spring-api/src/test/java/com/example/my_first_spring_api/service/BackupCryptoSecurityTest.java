package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security & cryptographic integrity regression tests for BackupCrypto.java.
 * Validates authenticated encryption with associated data (AES-256-GCM AEAD):
 * - Successful round-trip encryption and authenticated decryption
 * - Rejection of wrong decryption passphrase (AEADBadTagException)
 * - Rejection of tampered ciphertext payload
 * - Rejection of tampered salt/IV in header
 * - Fail-closed cleanup: zero plaintext leakage on any authentication failure
 * - Enforcement of minimum passphrase complexity (>= 16 characters)
 */
class BackupCryptoSecurityTest {

    private static Path backupCryptoPath;
    private static String javaBin;

    @BeforeAll
    static void setUp() {
        // Resolve java binary
        String javaHome = System.getProperty("java.home");
        Path bin = Paths.get(javaHome, "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        javaBin = bin.toFile().exists() ? bin.toAbsolutePath().toString() : "java";

        // Resolve BackupCrypto.java relative to repository root or working directory
        Path candidate1 = Paths.get("../scripts/BackupCrypto.java").toAbsolutePath().normalize();
        Path candidate2 = Paths.get("scripts/BackupCrypto.java").toAbsolutePath().normalize();
        if (Files.exists(candidate1)) {
            backupCryptoPath = candidate1;
        } else if (Files.exists(candidate2)) {
            backupCryptoPath = candidate2;
        } else {
            throw new IllegalStateException("Could not locate scripts/BackupCrypto.java at " + candidate1 + " or " + candidate2);
        }
    }

    private static record ProcessResult(int exitCode, String stdout, String stderr) {}

    private ProcessResult runCrypto(String mode, Path input, Path output, String passphrase) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                backupCryptoPath.toString(),
                mode,
                input.toAbsolutePath().toString(),
                output.toAbsolutePath().toString()
        );
        if (passphrase != null) {
            pb.environment().put("BACKUP_ENCRYPTION_PASSPHRASE", passphrase);
        } else {
            pb.environment().remove("BACKUP_ENCRYPTION_PASSPHRASE");
        }
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, stdout, stderr);
    }

    private static String computeSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("CRYPTO-01: Successful AES-256-GCM AEAD encryption and authenticated decryption round-trip")
    void testSuccessfulEncryptionAndDecryptionRoundtrip(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("source.dump");
        Path encryptedFile = tempDir.resolve("source.dump.enc");
        Path decryptedFile = tempDir.resolve("source.dump.dec");

        String syntheticPayload = "-- SYNTHETIC DATABASE DUMP FOR SECURITY VERIFICATION\n"
                + "INSERT INTO users VALUES (1, 'NonProductionUser_101');\n"
                + "INSERT INTO orders VALUES (500, 'ORD-SEC-001', 350.00);\n";
        byte[] sourceBytes = syntheticPayload.getBytes(StandardCharsets.UTF_8);
        Files.write(sourceFile, sourceBytes);
        String sourceHash = computeSha256(sourceBytes);

        String passphrase = "SyntheticValidSecretPassphrase2026!";

        // Encrypt
        ProcessResult encResult = runCrypto("encrypt", sourceFile, encryptedFile, passphrase);
        assertThat(encResult.exitCode()).as("Encryption must succeed with 0").isEqualTo(0);
        assertThat(encResult.stdout()).contains("AES-256-GCM AEAD encryption successful");
        assertThat(Files.exists(encryptedFile)).isTrue();
        assertThat(Files.size(encryptedFile)).isGreaterThan(sourceBytes.length);

        // Decrypt
        ProcessResult decResult = runCrypto("decrypt", encryptedFile, decryptedFile, passphrase);
        assertThat(decResult.exitCode()).as("Decryption must succeed with 0").isEqualTo(0);
        assertThat(decResult.stdout()).contains("AES-256-GCM AEAD decryption and tag verification successful");
        assertThat(Files.exists(decryptedFile)).isTrue();

        byte[] decryptedBytes = Files.readAllBytes(decryptedFile);
        assertThat(decryptedBytes).isEqualTo(sourceBytes);
        assertThat(computeSha256(decryptedBytes)).isEqualTo(sourceHash);
    }

    @Test
    @DisplayName("CRYPTO-02: Decryption with incorrect passphrase fails fast with AEADBadTagException (exit code 2) and deletes output")
    void testWrongPassphraseRejectionFailsFastWithTagMismatch(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("source.dump");
        Path encryptedFile = tempDir.resolve("source.dump.enc");
        Path decryptedFile = tempDir.resolve("wrong_output.dump");

        Files.writeString(sourceFile, "DUMMY_DATABASE_CONTENT_TEST_WRONG_PASS");
        String correctPassphrase = "OriginalCorrectPassphrase2026!Protected";
        String wrongPassphrase = "IncorrectPassphraseAttempt2026!Failed";

        // Encrypt with correct passphrase
        ProcessResult encResult = runCrypto("encrypt", sourceFile, encryptedFile, correctPassphrase);
        assertThat(encResult.exitCode()).isEqualTo(0);

        // Attempt decryption with wrong passphrase
        ProcessResult decResult = runCrypto("decrypt", encryptedFile, decryptedFile, wrongPassphrase);
        assertThat(decResult.exitCode()).as("Wrong passphrase must exit with code 2").isEqualTo(2);
        assertThat(decResult.stderr()).contains("Authentication tag mismatch");
        assertThat(Files.exists(decryptedFile)).as("No decrypted file must persist on authentication failure").isFalse();
    }

    @Test
    @DisplayName("CRYPTO-03: Tampered ciphertext payload triggers authentication tag mismatch and leaves zero output")
    void testTamperedCiphertextRejectionThrowsAEADBadTagException(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("source.dump");
        Path encryptedFile = tempDir.resolve("source.dump.enc");
        Path tamperedEncryptedFile = tempDir.resolve("tampered.dump.enc");
        Path outputDecryptedFile = tempDir.resolve("tampered_decrypted.dump");

        Files.writeString(sourceFile, "DUMMY_DATABASE_CONTENT_TEST_TAMPERING_SAFETY");
        String passphrase = "TamperDetectionPassphrase2026!Active";

        ProcessResult encResult = runCrypto("encrypt", sourceFile, encryptedFile, passphrase);
        assertThat(encResult.exitCode()).isEqualTo(0);

        // Tamper with 1 byte in the ciphertext payload (offset 35)
        byte[] encBytes = Files.readAllBytes(encryptedFile);
        encBytes[35] = (byte) (encBytes[35] ^ 0xFF);
        Files.write(tamperedEncryptedFile, encBytes);

        // Attempt decryption on tampered file
        ProcessResult decResult = runCrypto("decrypt", tamperedEncryptedFile, outputDecryptedFile, passphrase);
        assertThat(decResult.exitCode()).as("Tampered payload must exit with code 2").isEqualTo(2);
        assertThat(decResult.stderr()).contains("Authentication tag mismatch");
        assertThat(Files.exists(outputDecryptedFile)).as("No decrypted file must persist on tamper").isFalse();
    }

    @Test
    @DisplayName("CRYPTO-04: Tampered salt/IV header triggers authentication failure and prevents file creation")
    void testTamperedHeaderSaltRejectionThrowsAEADBadTagException(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("source.dump");
        Path encryptedFile = tempDir.resolve("source.dump.enc");
        Path headerTamperedFile = tempDir.resolve("header_tampered.dump.enc");
        Path outputDecryptedFile = tempDir.resolve("header_tampered_decrypted.dump");

        Files.writeString(sourceFile, "DUMMY_DATABASE_CONTENT_TEST_HEADER_INTEGRITY");
        String passphrase = "HeaderIntegrityPassphrase2026!Active";

        ProcessResult encResult = runCrypto("encrypt", sourceFile, encryptedFile, passphrase);
        assertThat(encResult.exitCode()).isEqualTo(0);

        // Tamper with 1 byte in the Salt header (offset 5)
        byte[] encBytes = Files.readAllBytes(encryptedFile);
        encBytes[5] = (byte) (encBytes[5] ^ 0xFF);
        Files.write(headerTamperedFile, encBytes);

        ProcessResult decResult = runCrypto("decrypt", headerTamperedFile, outputDecryptedFile, passphrase);
        assertThat(decResult.exitCode()).as("Tampered header must exit with code 2").isEqualTo(2);
        assertThat(decResult.stderr()).contains("Authentication tag mismatch");
        assertThat(Files.exists(outputDecryptedFile)).as("No decrypted file must persist on header tamper").isFalse();
    }

    @Test
    @DisplayName("CRYPTO-05: Passphrase shorter than 16 characters is rejected fail-closed")
    void testPassphraseTooShortRejected(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("source.dump");
        Path encryptedFile = tempDir.resolve("short.dump.enc");

        Files.writeString(sourceFile, "SAMPLE_PAYLOAD_FOR_SHORT_PASS_TEST");

        ProcessResult encResult = runCrypto("encrypt", sourceFile, encryptedFile, "ShortPass123");
        assertThat(encResult.exitCode()).as("Short passphrase must exit with code 1").isEqualTo(1);
        assertThat(encResult.stderr()).contains("must be set and at least 16 characters long");
        assertThat(Files.exists(encryptedFile)).isFalse();
    }

    @Test
    @DisplayName("CRYPTO-06: Truncated or empty archive (< 44 bytes) is rejected fail-closed")
    void testTruncatedArchiveRejected(@TempDir Path tempDir) throws Exception {
        Path truncatedFile = tempDir.resolve("truncated.dump.enc");
        Path decryptedFile = tempDir.resolve("truncated.dump");

        Files.write(truncatedFile, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});

        ProcessResult decResult = runCrypto("decrypt", truncatedFile, decryptedFile, "ValidPassphraseLength2026!Strong");
        assertThat(decResult.exitCode()).as("Truncated archive must exit with code 1").isEqualTo(1);
        assertThat(decResult.stderr()).contains("too small to be an authentic GCM archive");
        assertThat(Files.exists(decryptedFile)).isFalse();
    }
}
