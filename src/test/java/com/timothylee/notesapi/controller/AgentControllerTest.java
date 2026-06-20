package com.timothylee.notesapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timothylee.notesapi.config.SecurityConfig;
import com.timothylee.notesapi.dto.request.AgentChatRequest;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.security.JwtUtil;
import com.timothylee.notesapi.security.UserDetailsServiceImpl;
import com.timothylee.notesapi.service.AgentService;
import com.timothylee.notesapi.service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
@Import(SecurityConfig.class)
class AgentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AgentService agentService;
    @MockBean JwtUtil jwtUtil;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .password("hashed")
                .fullName("Alice")
                .build();
    }

    @Test
    void chat_authenticated_returns200() throws Exception {
        when(agentService.chat(eq("list my notes"), eq(testUser.getId()))).thenReturn("You have 2 notes.");

        mockMvc.perform(post("/api/v1/agent/chat")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AgentChatRequest("list my notes"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("You have 2 notes."));
    }

    @Test
    void chat_blankMessage_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/agent/chat")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void chat_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }
}
