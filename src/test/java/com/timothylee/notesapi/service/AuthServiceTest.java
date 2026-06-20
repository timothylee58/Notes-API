package com.timothylee.notesapi.service;

import com.timothylee.notesapi.dto.request.LoginRequest;
import com.timothylee.notesapi.dto.request.RegisterRequest;
import com.timothylee.notesapi.exception.UnauthorizedException;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.repository.UserRepository;
import com.timothylee.notesapi.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock AuthenticationManager authenticationManager;
    @Mock TokenBlacklistService tokenBlacklistService;
    @InjectMocks AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .password("hashed")
                .fullName("Alice Tan")
                .build();
    }

    @Test
    void registerUser_success() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(testUser);
        when(jwtUtil.generateAccessToken(any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");
        when(jwtUtil.getAccessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.registerUser(new RegisterRequest("alice@example.com", "password1", "Alice Tan"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void registerUser_duplicateEmail_throws() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.registerUser(new RegisterRequest("alice@example.com", "password1", "Alice")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void login_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateAccessToken(testUser)).thenReturn("access");
        when(jwtUtil.generateRefreshToken(testUser)).thenReturn("refresh");
        when(jwtUtil.getAccessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.login(new LoginRequest("alice@example.com", "password1"));

        assertThat(response.accessToken()).isEqualTo("access");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_wrongPassword_throws() {
        doThrow(new BadCredentialsException("bad creds"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logout_blacklistsToken() {
        Instant expiry = Instant.now().plusSeconds(300);
        when(jwtUtil.extractJti("token123")).thenReturn("jti-abc");
        when(jwtUtil.extractExpiration("token123")).thenReturn(expiry);

        authService.logout("token123");

        verify(tokenBlacklistService).blacklistToken("jti-abc", expiry);
    }

    @Test
    void refreshToken_rotatesTokens() {
        Instant expiry = Instant.now().plusSeconds(604800);
        when(jwtUtil.extractEmail("old-refresh")).thenReturn("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.isTokenValid("old-refresh", testUser)).thenReturn(true);
        when(jwtUtil.extractJti("old-refresh")).thenReturn("old-jti");
        when(jwtUtil.extractExpiration("old-refresh")).thenReturn(expiry);
        when(jwtUtil.generateAccessToken(testUser)).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(testUser)).thenReturn("new-refresh");
        when(jwtUtil.getAccessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.refreshToken("old-refresh");

        // Old refresh token must be blacklisted
        verify(tokenBlacklistService).blacklistToken("old-jti", expiry);
        // Response must carry the NEW refresh token
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.accessToken()).isEqualTo("new-access");
    }

    @Test
    void refreshToken_invalidToken_throws() {
        when(jwtUtil.extractEmail("bad-token")).thenReturn("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.isTokenValid("bad-token", testUser)).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("bad-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(tokenBlacklistService, never()).blacklistToken(any(), any());
    }
}
