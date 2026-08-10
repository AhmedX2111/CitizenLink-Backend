package com.ntg.CitizenLink.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtBlocklist}.
 *
 * Covers the block/isBlocked behavior and the cleanup() scheduled task
 * that prevents the blocklist map from growing without bound.
 */
class JwtBlocklistTest {

    private JwtBlocklist blocklist;

    @BeforeEach
    void setUp() {
        blocklist = new JwtBlocklist();
    }

    @Test
    void isBlocked_returnsTrue_forBlockedJti() {
        blocklist.block("jti-active", futureDate(60000));

        assertThat(blocklist.isBlocked("jti-active")).isTrue();
    }

    @Test
    void isBlocked_returnsFalse_forUnknownJti() {
        assertThat(blocklist.isBlocked("never-blocked")).isFalse();
    }

    @Test
    void isBlocked_evictsExpiredEntryLazily() {
        blocklist.block("jti-expired", pastDate(1000));

        assertThat(blocklist.isBlocked("jti-expired")).isFalse();
        assertThat(blocklist.isBlocked("jti-expired")).isFalse();
    }

    @Test
    void cleanup_removesExpiredEntries_keepsValidOnes() {
        blocklist.block("jti-expired", pastDate(1000));
        blocklist.block("jti-valid", futureDate(60000));

        blocklist.cleanup();

        assertThat(blocklist.isBlocked("jti-expired")).isFalse();
        assertThat(blocklist.isBlocked("jti-valid")).isTrue();
    }

    @Test
    void cleanup_keepsAllEntries_whenNoneExpired() {
        blocklist.block("jti-1", futureDate(60000));
        blocklist.block("jti-2", futureDate(120000));

        blocklist.cleanup();

        assertThat(blocklist.isBlocked("jti-1")).isTrue();
        assertThat(blocklist.isBlocked("jti-2")).isTrue();
    }

    @Test
    void cleanup_removesNothing_whenEmpty() {
        blocklist.cleanup();

        assertThat(blocklist.isBlocked("anything")).isFalse();
    }

    private Date futureDate(long millisAhead) {
        return new Date(System.currentTimeMillis() + millisAhead);
    }

    private Date pastDate(long millisBehind) {
        return new Date(System.currentTimeMillis() - millisBehind);
    }
}
