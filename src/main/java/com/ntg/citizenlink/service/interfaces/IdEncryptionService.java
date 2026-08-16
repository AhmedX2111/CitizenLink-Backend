package com.ntg.citizenlink.service.interfaces;

import org.springframework.stereotype.Service;
import java.util.UUID;

public interface IdEncryptionService {

    /**
     * Encrypts a UUID into a URL-safe string.
     */
    String encryptId(UUID id);

    /**
     * Decrypts an encrypted ID back to UUID.
     */
    UUID decryptId(String encryptedId);

    /**
     * Validates if a string is an encrypted ID.
     */
    boolean isValidEncryptedId(String id);

    /**
     * Safely decrypt ID without throwing exceptions.
     */
    UUID decryptIdSafely(String encryptedId);
}
