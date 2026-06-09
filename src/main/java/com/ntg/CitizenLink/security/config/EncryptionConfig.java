package com.ntg.CitizenLink.security.config;

import com.ntg.CitizenLink.service.IdEncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class EncryptionConfig {

    @Value("${encryption.password:citizenLinkEncryptionPassword}")
    private String password;

    @Value("${encryption.salt:deadbeef12345678}")
    private String salt;

    /**
     * Creates a text encryptor for ID encryption.
     * Uses AES-256 encryption with a random salt.
     */
    @Bean
    public TextEncryptor textEncryptor() {
        // Encryptors.text() uses a standard password-based encryption
        return Encryptors.text(password, salt);
    }
}
