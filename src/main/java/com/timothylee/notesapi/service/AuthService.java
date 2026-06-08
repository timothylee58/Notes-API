package com.timothylee.notesapi.service;

import com.timothylee.notesapi.dto.request.LoginRequest;
import com.timothylee.notesapi.dto.request.RegisterRequest;
import com.timothylee.notesapi.dto.response.AuthResponse;
import com.timothylee.notesapi.exception.UnauthorizedException;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.repository.UserRepository;
import com.timothylee.notesapi.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        var user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .build();

        userRepository.save(user);
        return issueTokenPair(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var user = userRepository.findByEmail(request.email()).orElseThrow();
        return issueTokenPair(user);
    }

    public AuthResponse refresh(String refreshToken) {
        var email = jwtUtil.extractUsername(refreshToken);
        var user = userRepository.findByEmail(email).orElseThrow();
        if (!jwtUtil.isTokenValid(refreshToken, user)) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }
        return new AuthResponse(jwtUtil.generateAccessToken(user), refreshToken);
    }

    public void logout(String accessToken) {
        tokenBlacklistService.blacklist(
                jwtUtil.extractJti(accessToken),
                jwtUtil.extractExpiration(accessToken));
    }

    private AuthResponse issueTokenPair(User user) {
        return new AuthResponse(jwtUtil.generateAccessToken(user), jwtUtil.generateRefreshToken(user));
    }
}
