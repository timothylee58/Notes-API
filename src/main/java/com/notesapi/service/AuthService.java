package com.notesapi.service;

import com.notesapi.dto.request.LoginRequest;
import com.notesapi.dto.request.RegisterRequest;
import com.notesapi.dto.response.AuthResponse;
import com.notesapi.entity.User;
import com.notesapi.exception.UserAlreadyExistsException;
import com.notesapi.repository.UserRepository;
import com.notesapi.security.JwtService;
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
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
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
        var email = jwtService.extractUsername(refreshToken);
        var user = userRepository.findByEmail(email).orElseThrow();
        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new com.notesapi.exception.InvalidTokenException("Refresh token is invalid or expired");
        }
        return new AuthResponse(jwtService.generateAccessToken(user), refreshToken);
    }

    public void logout(String accessToken) {
        var jti = jwtService.extractJti(accessToken);
        var expiration = jwtService.extractExpiration(accessToken);
        tokenBlacklistService.blacklist(jti, expiration);
    }

    private AuthResponse issueTokenPair(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user)
        );
    }
}
