package com.timothylee.notesapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.timothylee.notesapi.dto.response.NoteResponse;
import com.timothylee.notesapi.dto.response.PagedResponse;
import com.timothylee.notesapi.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock NoteService noteService;
    @InjectMocks ToolExecutor toolExecutor;

    private UUID userId;
    private UUID noteId;
    private NoteResponse sampleNote;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        toolExecutor = new ToolExecutor(noteService, om);
        userId = UUID.randomUUID();
        noteId = UUID.randomUUID();
        sampleNote = new NoteResponse(noteId, "Title", "Content", List.of("tag"), Instant.now(), Instant.now());
    }

    @Test
    void list_notes_invokes_getNotes_with_correct_userId() {
        when(noteService.getNotes(eq(userId), eq(null), eq(20)))
                .thenReturn(new PagedResponse<>(List.of(sampleNote), null, false, 20));

        String result = toolExecutor.execute("list_notes", Map.of(), userId);

        assertThat(result).contains("Title");
        verify(noteService).getNotes(userId, null, 20);
    }

    @Test
    void get_note_invokes_getNoteById_with_correct_ids() {
        when(noteService.getNoteById(userId, noteId)).thenReturn(sampleNote);

        String result = toolExecutor.execute("get_note", Map.of("id", noteId.toString()), userId);

        assertThat(result).contains("Title");
        verify(noteService).getNoteById(userId, noteId);
    }

    @Test
    void create_note_invokes_createNote() {
        when(noteService.createNote(eq(userId), any())).thenReturn(sampleNote);

        String result = toolExecutor.execute("create_note",
                Map.of("title", "Title", "content", "Content", "tags", List.of("tag")), userId);

        assertThat(result).contains("Title");
        verify(noteService).createNote(eq(userId), any());
    }

    @Test
    void update_note_invokes_updateNote() {
        when(noteService.updateNote(eq(userId), eq(noteId), any())).thenReturn(sampleNote);

        String result = toolExecutor.execute("update_note",
                Map.of("id", noteId.toString(), "title", "Updated", "content", "New"), userId);

        assertThat(result).contains("Title");
        verify(noteService).updateNote(eq(userId), eq(noteId), any());
    }

    @Test
    void delete_note_invokes_deleteNote_and_returns_deleted() {
        doNothing().when(noteService).deleteNote(userId, noteId);

        String result = toolExecutor.execute("delete_note", Map.of("id", noteId.toString()), userId);

        assertThat(result).isEqualTo("deleted");
        verify(noteService).deleteNote(userId, noteId);
    }

    @Test
    void unknown_tool_throws_exception() {
        assertThatThrownBy(() -> toolExecutor.execute("unknown_tool", Map.of(), userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown tool");
    }

    @Test
    void hallucinated_userId_in_input_is_ignored() {
        // Even if LLM puts a userId in the tool input, it must be ignored
        UUID attackerId = UUID.randomUUID();
        when(noteService.getNoteById(userId, noteId)).thenReturn(sampleNote);

        Map<String, Object> input = new HashMap<>();
        input.put("id", noteId.toString());
        input.put("userId", attackerId.toString()); // hallucinated — must be ignored

        toolExecutor.execute("get_note", input, userId);

        // Verify the real userId was used, not the hallucinated one
        verify(noteService).getNoteById(userId, noteId);
        verify(noteService, never()).getNoteById(eq(attackerId), any());
    }

    @Test
    void get_note_owned_by_other_user_throws_404() {
        when(noteService.getNoteById(userId, noteId))
                .thenThrow(new ResourceNotFoundException("Note not found: " + noteId));

        assertThatThrownBy(() -> toolExecutor.execute("get_note", Map.of("id", noteId.toString()), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
