package org.flymars.devtools.midas.security;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption utility for sensitive data like passwords, tokens, and API keys
 * Uses AES-256-GCM encryption for strong security
 */
public class EncryptionUtil {
    private static final Logger LOG = Logger.getInstance(EncryptionUtil.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256; // bits
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Encrypt a string value (application-level)
     * @param plainText the text to encrypt
     * @return Base64-encoded encrypted data (IV + ciphertext + tag)
     */
    public static String encryptForApp(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // Generate secret key for application
            SecretKey secretKey = generateKeyForApplication();

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            byte[] combined = byteBuffer.array();

            // Return Base64 encoded
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            LOG.error("Failed to encrypt data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt a string value (application-level)
     * @param encryptedText Base64-encoded encrypted data (IV + ciphertext + tag)
     * @return decrypted plain text
     */
    public static String decryptForApp(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // Generate secret key for application
            SecretKey secretKey = generateKeyForApplication();

            // Decode Base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // Extract IV
            ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            // Extract ciphertext
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("Failed to decrypt data", e);
            // Return empty string on decryption failure to prevent crashes
            return "";
        }
    }

    /**
     * Encrypt a string value (project-level, kept for backward compatibility)
     * @param plainText the text to encrypt
     * @param project the project used to generate encryption key
     * @return Base64-encoded encrypted data (IV + ciphertext + tag)
     */
    public static String encrypt(String plainText, Project project) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // Generate secret key from project ID
            SecretKey secretKey = generateKeyFromProject(project);

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            byte[] combined = byteBuffer.array();

            // Return Base64 encoded
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            LOG.error("Failed to encrypt data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt a string value (project-level, kept for backward compatibility)
     * @param encryptedText Base64-encoded encrypted data (IV + ciphertext + tag)
     * @param project the project used to generate encryption key
     * @return decrypted plain text
     */
    public static String decrypt(String encryptedText, Project project) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // Generate secret key from project ID
            SecretKey secretKey = generateKeyFromProject(project);

            // Decode Base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // Extract IV
            ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            // Extract ciphertext
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("Failed to decrypt data", e);
            // Return empty string on decryption failure to prevent crashes
            return "";
        }
    }

    /**
     * Generate secret key from project ID
     * This ensures each project has a unique encryption key
     */
    private static SecretKey generateKeyFromProject(Project project) {
        try {
            // Use project ID as seed for key generation
            String projectId = project.getLocationHash();

            // Generate a key from the project ID
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((projectId + "midas-encryption-salt").getBytes(StandardCharsets.UTF_8));

            // Use first 32 bytes (256 bits) for AES-256
            byte[] keyBytes = new byte[32];
            System.arraycopy(hash, 0, keyBytes, 0, Math.min(hash.length, 32));

            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            LOG.error("Failed to generate encryption key", e);
            throw new RuntimeException("Key generation failed", e);
        }
    }

    /**
     * Generate secret key for application-level encryption
     * Uses user home directory to generate a consistent key across all projects
     */
    private static SecretKey generateKeyForApplication() {
        try {
            // Use user home directory as seed for key generation
            String userHome = System.getProperty("user.home");

            // Generate a key from the user home directory
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((userHome + "midas-app-encryption-salt").getBytes(StandardCharsets.UTF_8));

            // Use first 32 bytes (256 bits) for AES-256
            byte[] keyBytes = new byte[32];
            System.arraycopy(hash, 0, keyBytes, 0, Math.min(hash.length, 32));

            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            LOG.error("Failed to generate application encryption key", e);
            throw new RuntimeException("Application key generation failed", e);
        }
    }

    /**
     * Check if a string appears to be encrypted (Base64 with IV prefix)
     */
    public static boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            // Encrypted values are Base64 and contain IV prefix
            // A rough check: length should be reasonable for AES-GCM
            return value.length() > GCM_IV_LENGTH &&
                   value.matches("^[A-Za-z0-9+/]+=*$");
        } catch (Exception e) {
            return false;
        }
    }
}
