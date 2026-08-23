package com.ntg.citizenlink.service.impl;

import com.ntg.citizenlink.exception.InvalidEncryptedIdException;
import com.ntg.citizenlink.service.interfaces.IdEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class IdEncryptionServiceImpl implements IdEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 32;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    @Autowired
    public IdEncryptionServiceImpl(@Value("${encryption.secret-key}") String secretKeyString) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretKeyString.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ENCRYPTION_SECRET_KEY must be a Base64-encoded key", e);
        }

        if (keyBytes.length != AES_KEY_SIZE) {
            throw new IllegalStateException(
                    "ENCRYPTION_SECRET_KEY must decode to exactly " + AES_KEY_SIZE
                            + " bytes for AES-256, got " + keyBytes.length);
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();

        log.info("IdEncryptionService initialized with AES-256/GCM");
    }

    @Override
    public String encryptId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }

        try {
            String idString = id.toString();

            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(idString.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);

        } catch (Exception e) {
            // M-19: this path only ever handles server-owned UUIDs (AuthServiceImpl),
            // so an ERROR with a stack trace is appropriate for diagnostics — but the
            // plaintext UUID this service exists to hide is never written to the log.
            log.error("Failed to encrypt ID", e);
            throw new RuntimeException("Failed to encrypt ID", e);
        }
    }

    @Override
    public UUID decryptId(String encryptedId) {
        if (encryptedId == null || encryptedId.isEmpty()) {
            throw new IllegalArgumentException("Encrypted ID cannot be null or empty");
        }

        try {
            byte[] combined = Base64.getUrlDecoder().decode(encryptedId);

            if (combined.length < GCM_IV_LENGTH) {
                throw new InvalidEncryptedIdException("Invalid encrypted ID format");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String idString = new String(decryptedBytes, StandardCharsets.UTF_8);

            return UUID.fromString(idString);

        } catch (InvalidEncryptedIdException e) {
            throw e;
        } catch (Exception e) {
            // M-19: the input is attacker-controlled (it arrives straight from a
            // request path). Log at WARN without the throwable and log only the
            // length — never the ciphertext — so garbage input cannot flood the
            // log files or inject log content. Throws a typed 400 exception.
            log.warn("Failed to decrypt ID (input length: {})", encryptedId.length());
            throw new InvalidEncryptedIdException("Invalid or corrupted encrypted ID.");
        }
    }

    @Override
    public boolean isValidEncryptedId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(id);
            return decoded.length > GCM_IV_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public UUID decryptIdSafely(String encryptedId) {
        try {
            return decryptId(encryptedId);
        } catch (Exception e) {
            // M-19: routine control flow (returns null so callers treat the id as
            // absent), so DEBUG and no throwable — and never the ciphertext.
            log.debug("Failed to safely decrypt ID (input length: {})",
                    encryptedId == null ? -1 : encryptedId.length());
            return null;
        }
    }
}
