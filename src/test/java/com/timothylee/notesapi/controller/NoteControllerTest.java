package com.timothylee.notesapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timothylee.notesapi.dto.request.NoteRequest;
import com.timothylee.notesapi.dto.response.NoteResponse;
import com.timothylee.notesapi.dto.response.PagedResponse;
import com.timothylee.notesapi.exception.ResourceNotFoundException;
import com.timothylee.notesapi.config.SecurityConfig;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.security.JwtUtil;
import com.timothylee.notesapi.security.UserDetailsServiceImpl;
import com.timothylee.notesapi.service.NoteService;
import com.timothylee.notesapi.service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NoteController.class)
@Import(SecurityConfig.class)
class NoteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean NoteService noteService;
    @MockBean JwtUtil jwtUtil;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean UserDetailsServiceImpl userDetailsServiceImpl;

    private User testUser;
    private UUID noteId;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .password("hashed")
                .fullName("Alice")
                .build();
        noteId = UUID.randomUUID();
    }

    @Test
    void testGetNotes_authenticated() throws Exception {
        var page = new PagedResponse<>(List.of(sampleNote()), null, false, 20);
        when(noteService.getNotes(eq(testUser.getId()), any(), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/notes")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("Title"));
    }

    @Test
    void testGetNotes_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/v1/notes"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    @Test
    void testCreateNote_success() throws Exception {
        when(noteService.createNote(eq(testUser.getId()), any())).thenReturn(sampleNote());

        mockMvc.perform(post("/api/v1/notes")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NoteRequest("Title", "content", List.of("tag")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Title"));
    }

    @Test
    void testCreateNote_validationFail_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/notes")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void testDeleteNote_notOwner_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Note not found: " + noteId))
                .when(noteService).deleteNote(eq(testUser.getId()), eq(noteId));

        mockMvc.perform(delete("/api/v1/notes/{id}", noteId)
                        .with(user(testUser)))
                .andExpect(status().isNotFound());
    }

    private NoteResponse sampleNote() {
        return new NoteResponse(noteId, "Title", "content", List.of("tag"), Instant.now(), Instant.now());
    }
}
