package com.timothylee.notesapi.security;

import com.timothylee.notesapi.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-must-be-at-least-32-bytes!!");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiryMs", 900_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiryMs", 604_800_000L);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .password("hashed")
                .fullName("Alice")
                .build();
    }

    @Test
    void generateAccessToken_extractsEmailCorrectly() {
        String token = jwtUtil.generateAccessToken(testUser);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void generateRefreshToken_extractsEmailCorrectly() {
        String token = jwtUtil.generateRefreshToken(testUser);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void accessAndRefreshTokens_haveDifferentJtis() {
        String access = jwtUtil.generateAccessToken(testUser);
        String refresh = jwtUtil.generateRefreshToken(testUser);
        assertThat(jwtUtil.extractJti(access)).isNotEqualTo(jwtUtil.extractJti(refresh));
    }

    @Test
    void isTokenValid_returnsTrueForFreshToken() {
        String token = jwtUtil.generateAccessToken(testUser);
        assertThat(jwtUtil.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForWrongUser() {
        String token = jwtUtil.generateAccessToken(testUser);
        User other = User.builder().id(UUID.randomUUID()).email("bob@example.com")
                .password("x").fullName("Bob").build();
        assertThat(jwtUtil.isTokenValid(token, other)).isFalse();
    }

    @Test
    void isTokenExpired_returnsFalseForFreshToken() {
        String token = jwtUtil.generateAccessToken(testUser);
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    void extractExpiration_isInFuture() {
        String token = jwtUtil.generateAccessToken(testUser);
        assertThat(jwtUtil.extractExpiration(token)).isAfter(Instant.now());
    }

    @Test
    void getAccessTokenExpiryMs_returnsConfiguredValue() {
        assertThat(jwtUtil.getAccessTokenExpiryMs()).isEqualTo(900_000L);
    }
}
