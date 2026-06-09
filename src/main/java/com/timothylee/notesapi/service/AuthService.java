package com.timothylee.notesapi.service;

import com.timothylee.notesapi.dto.request.LoginRequest;
import com.timothylee.notesapi.dto.request.RegisterRequest;
import com.timothylee.notesapi.dto.response.AuthResponse;
import com.timothylee.notesapi.exception.UnauthorizedException;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.repository.UserRepository;
import com.timothylee.notesapi.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        var user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .build();

        userRepository.save(user);
        log.info("Registered new user email={}", user.getEmail());
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var user = userRepository.findByEmail(request.email()).orElseThrow();
        log.info("User logged in email={}", user.getEmail());
        return buildAuthResponse(user);
    }

    public void logout(String token) {
        String jti = jwtUtil.extractJti(token);
        tokenBlacklistService.blacklistToken(jti, jwtUtil.extractExpiration(token));
    }

    public AuthResponse refreshToken(String refreshToken) {
        String email = jwtUtil.extractEmail(refreshToken);
        var user = userRepository.findByEmail(email).orElseThrow();
        if (!jwtUtil.isTokenValid(refreshToken, user)) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }
        String newAccessToken = jwtUtil.generateAccessToken(user);
        return AuthResponse.of(newAccessToken, refreshToken, jwtUtil.getAccessTokenExpiryMs());
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.of(
                jwtUtil.generateAccessToken(user),
                jwtUtil.generateRefreshToken(user),
                jwtUtil.getAccessTokenExpiryMs());
    }
}
