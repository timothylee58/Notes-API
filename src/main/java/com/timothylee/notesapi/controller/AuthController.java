package com.timothylee.notesapi.controller;

import com.timothylee.notesapi.dto.request.LoginRequest;
import com.timothylee.notesapi.dto.request.RefreshTokenRequest;
import com.timothylee.notesapi.dto.request.RegisterRequest;
import com.timothylee.notesapi.dto.response.AuthResponse;
import com.timothylee.notesapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new account", description = "Creates a user account and returns a JWT access + refresh token pair.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "400", description = "Email already registered or validation failed", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.registerUser(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and obtain a token pair", description = "Authenticates credentials and returns a JWT access + refresh token pair.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid email or password", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate current access token", description = "Blacklists the current JWT in Redis — subsequent requests with this token are rejected.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader.substring(7));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token", description = "Exchanges a valid refresh token for a new access + refresh token pair. The supplied refresh token is immediately invalidated (rotation).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token rotated"),
        @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refreshToken(request.refreshToken());
    }
}
