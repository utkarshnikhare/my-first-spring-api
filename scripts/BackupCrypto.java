import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * SocioMart Production Authenticated Encryption Utility.
 * Implements AES-256-GCM (AEAD) with PBKDF2WithHmacSHA256 key derivation.
 *
 * Payload Structure:
 *   [16 bytes: Cryptographically Random Salt]
 *   [12 bytes: Cryptographically Random Nonce/IV]
 *   [N bytes: Ciphertext + 16 bytes Authentication Tag]
 */
public class BackupCrypto {

    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12; // 96-bit nonce recommended by NIST SP 800-38D
    private static final int TAG_BITS = 128; // 128-bit authentication tag
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int BUFFER_SIZE = 64 * 1024;

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java BackupCrypto.java <encrypt|decrypt> <input-file> <output-file>");
            System.exit(1);
        }

        String mode = args[0];
        String inputFile = args[1];
        String outputFile = args[2];

        String envPass = System.getenv("BACKUP_ENCRYPTION_PASSPHRASE");
        if (envPass == null || envPass.length() < 16) {
            System.err.println("[-] ERROR: BACKUP_ENCRYPTION_PASSPHRASE must be set and at least 16 characters long.");
            System.exit(1);
        }
        char[] passphrase = envPass.toCharArray();

        try {
            if ("encrypt".equalsIgnoreCase(mode)) {
                encrypt(inputFile, outputFile, passphrase);
                System.out.println("[+] AES-256-GCM AEAD encryption successful: " + outputFile);
            } else if ("decrypt".equalsIgnoreCase(mode)) {
                decrypt(inputFile, outputFile, passphrase);
                System.out.println("[+] AES-256-GCM AEAD decryption and tag verification successful: " + outputFile);
            } else {
                System.err.println("[-] ERROR: Unknown mode '" + mode + "'. Expected 'encrypt' or 'decrypt'.");
                System.exit(1);
            }
        } catch (javax.crypto.AEADBadTagException e) {
            System.err.println("[-] CRITICAL SECURITY ERROR: Authentication tag mismatch! Archive has been tampered with or passphrase is incorrect.");
            deleteIfExists(outputFile);
            System.exit(2);
        } catch (Exception e) {
            System.err.println("[-] ERROR: Cryptographic operation failed: " + e.getMessage());
            deleteIfExists(outputFile);
            System.exit(1);
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    private static void encrypt(String inputPath, String outputPath, char[] passphrase) throws Exception {
        File inFile = new File(inputPath);
        if (!inFile.exists() || inFile.length() == 0) {
            throw new FileNotFoundException("Input file does not exist or is empty: " + inputPath);
        }

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(salt);
        sr.nextBytes(iv);

        SecretKey secretKey = deriveKey(passphrase, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));

        File outFile = new File(outputPath);
        if (outFile.getParentFile() != null) {
            outFile.getParentFile().mkdirs();
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(inFile));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {

            // Write header (Salt + IV)
            out.write(salt);
            out.write(iv);

            byte[] inBuffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(inBuffer)) != -1) {
                byte[] cipherChunk = cipher.update(inBuffer, 0, read);
                if (cipherChunk != null && cipherChunk.length > 0) {
                    out.write(cipherChunk);
                }
            }

            byte[] finalChunk = cipher.doFinal();
            if (finalChunk != null && finalChunk.length > 0) {
                out.write(finalChunk);
            }
            out.flush();
        }
    }

    private static void decrypt(String inputPath, String outputPath, char[] passphrase) throws Exception {
        File inFile = new File(inputPath);
        if (!inFile.exists() || inFile.length() < SALT_BYTES + IV_BYTES + 16) {
            throw new IllegalArgumentException("Input file is missing or too small to be an authentic GCM archive: " + inputPath);
        }

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];

        File tempOutputFile = new File(outputPath + ".tmp." + System.currentTimeMillis());
        try (InputStream in = new BufferedInputStream(new FileInputStream(inFile))) {
            int saltRead = in.readNBytes(salt, 0, SALT_BYTES);
            if (saltRead != SALT_BYTES) {
                throw new EOFException("Incomplete salt in archive header.");
            }
            int ivRead = in.readNBytes(iv, 0, IV_BYTES);
            if (ivRead != IV_BYTES) {
                throw new EOFException("Incomplete nonce/IV in archive header.");
            }

            SecretKey secretKey = deriveKey(passphrase, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));

            // Stream decryption directly to temp disk file to prevent heap exhaustion (OOM) on large archives
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempOutputFile))) {
                byte[] inBuffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(inBuffer)) != -1) {
                    byte[] plainChunk = cipher.update(inBuffer, 0, read);
                    if (plainChunk != null && plainChunk.length > 0) {
                        out.write(plainChunk);
                    }
                }

                // doFinal verifies the 128-bit authentication tag. Throws AEADBadTagException on any tampering!
                byte[] finalChunk = cipher.doFinal();
                if (finalChunk != null && finalChunk.length > 0) {
                    out.write(finalChunk);
                }
                out.flush();
            }

            // Only move to final output path after complete tag verification succeeds
            File outFile = new File(outputPath);
            if (outFile.getParentFile() != null) {
                outFile.getParentFile().mkdirs();
            }
            Files.move(tempOutputFile.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            deleteIfExists(tempOutputFile.getAbsolutePath());
            deleteIfExists(outputPath);
            throw e;
        }
    }

    private static SecretKey deriveKey(char[] passphrase, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256);
        byte[] keyBytes = null;
        try {
            keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    private static void deleteIfExists(String path) {
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException ignored) {}
    }
}
