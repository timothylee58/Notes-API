package com.timothylee.notesapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timothylee.notesapi.dto.request.LoginRequest;
import com.timothylee.notesapi.dto.request.RegisterRequest;
import com.timothylee.notesapi.dto.response.AuthResponse;
import com.timothylee.notesapi.config.SecurityConfig;
import com.timothylee.notesapi.security.JwtUtil;
import com.timothylee.notesapi.security.UserDetailsServiceImpl;
import com.timothylee.notesapi.service.AuthService;
import com.timothylee.notesapi.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtUtil jwtUtil;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    private static final AuthResponse FAKE_AUTH =
            new AuthResponse("acc-token", "ref-token", "Bearer", 900L);

    @Test
    void testRegister_success() throws Exception {
        when(authService.registerUser(any())).thenReturn(FAKE_AUTH);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("alice@example.com", "password1", "Alice"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("acc-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void testRegister_duplicateEmail_returns400() throws Exception {
        when(authService.registerUser(any()))
                .thenThrow(new IllegalArgumentException("Email already registered: alice@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("alice@example.com", "password1", "Alice"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Email already registered: alice@example.com"));
    }

    @Test
    void testRegister_validationFail_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void testLogin_success() throws Exception {
        when(authService.login(any())).thenReturn(FAKE_AUTH);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("alice@example.com", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc-token"));
    }

    @Test
    void testLogin_wrongPassword_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("alice@example.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }
}
