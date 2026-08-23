package com.ntg.citizenlink.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for L-02: the secrets committed in the test configuration must be
 * clearly non-production dummy values, and the encryption key must decode to
 * the full AES-256 length so tests exercise the real GCM path (never the weak
 * zero-padding path the M-11 fix rejects).
 */
class TestConfigSecretsTest {

    @Test
    void testConfigUsesFullLengthEncryptionKey() throws Exception {
        Map<String, Object> jwt = jwtConfig();
        Map<String, Object> encryption = encryptionConfig();

        String encryptionKey = (String) encryption.get("secret-key");
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKey.trim());

        assertThat(keyBytes).hasSize(32);
    }

    @Test
    void testConfigJwtSecretIsClearlyNonProductionAndFullLength() throws Exception {
        Map<String, Object> jwt = jwtConfig();

        String jwtSecret = (String) jwt.get("secret-key");

        assertThat(jwtSecret.toLowerCase()).contains("test");
        assertThat(jwtSecret.toLowerCase()).contains("dummy");
        assertThat(jwtSecret.getBytes(StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(32);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jwtConfig() {
        return (Map<String, Object>) config().get("jwt");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> encryptionConfig() {
        return (Map<String, Object>) config().get("encryption");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> config() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("application-test.yml")) {
            Yaml yaml = new Yaml();
            return (Map<String, Object>) yaml.load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read application-test.yml", e);
        }
    }
}