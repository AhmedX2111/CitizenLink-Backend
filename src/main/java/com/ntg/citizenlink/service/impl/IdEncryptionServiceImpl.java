package com.ntg.citizenlink.service.impl;

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
        byte[] keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        byte[] paddedKey = new byte[AES_KEY_SIZE];

        for (int i = 0; i < AES_KEY_SIZE; i++) {
            if (i < keyBytes.length) {
                paddedKey[i] = keyBytes[i];
            } else {
                paddedKey[i] = (byte) (i % 256);
            }
        }

        this.secretKey = new SecretKeySpec(paddedKey, "AES");
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
            log.error("Failed to encrypt ID: {}", id, e);
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
                throw new IllegalArgumentException("Invalid encrypted ID format");
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

        } catch (Exception e) {
            log.error("Failed to decrypt ID: {}", encryptedId, e);
            throw new RuntimeException("Failed to decrypt ID. Invalid or corrupted data.", e);
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
            log.warn("Failed to safely decrypt ID: {}", encryptedId);
            return null;
        }
    }
}
