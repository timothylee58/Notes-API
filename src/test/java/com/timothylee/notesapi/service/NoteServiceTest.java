package com.timothylee.notesapi.service;

import com.timothylee.notesapi.dto.request.NoteRequest;
import com.timothylee.notesapi.exception.ResourceNotFoundException;
import com.timothylee.notesapi.model.Note;
import com.timothylee.notesapi.repository.NoteRepository;
import com.timothylee.notesapi.util.KeysetPaginationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock NoteRepository noteRepository;
    @Mock KeysetPaginationHelper paginationHelper;
    @InjectMocks NoteService noteService;

    private UUID userId;
    private UUID noteId;
    private Note note;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        noteId = UUID.randomUUID();
        note = Note.builder()
                .id(noteId)
                .userId(userId)
                .title("Test Note")
                .content("Content")
                .tags(List.of("java"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void getNotes_firstPage_noCursor() {
        when(noteRepository.findByUserIdWithKeyset(eq(userId), eq(null), eq(21)))
                .thenReturn(List.of(note));

        var page = noteService.getNotes(userId, null, 20);

        assertThat(page.data()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void getNotes_hasMore_returnsCursor() {
        var notes = buildNotes(userId, 21);
        when(noteRepository.findByUserIdWithKeyset(eq(userId), eq(null), eq(21))).thenReturn(notes);
        when(paginationHelper.encodeCursor(any())).thenReturn("cursor123");

        var page = noteService.getNotes(userId, null, 20);

        assertThat(page.data()).hasSize(20);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("cursor123");
    }

    @Test
    void getNotes_withCursor_decodesCursor() {
        Instant cursorInstant = Instant.now();
        when(paginationHelper.decodeCursor("cur")).thenReturn(cursorInstant);
        when(noteRepository.findByUserIdWithKeyset(eq(userId), eq(cursorInstant), eq(21)))
                .thenReturn(List.of());

        var page = noteService.getNotes(userId, "cur", 20);

        assertThat(page.data()).isEmpty();
        verify(paginationHelper).decodeCursor("cur");
    }

    @Test
    void createNote_savesAndReturnsResponse() {
        when(noteRepository.save(any())).thenReturn(note);

        var response = noteService.createNote(userId, new NoteRequest("Test Note", "Content", List.of("java")));

        assertThat(response.title()).isEqualTo("Test Note");
        verify(noteRepository).save(any());
    }

    @Test
    void getNoteById_success() {
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(note));

        var response = noteService.getNoteById(userId, noteId);

        assertThat(response.id()).isEqualTo(noteId);
    }

    @Test
    void getNoteById_notOwned_throws404() {
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNoteById(userId, noteId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateNote_success() {
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(noteRepository.save(note)).thenReturn(note);

        var response = noteService.updateNote(userId, noteId, new NoteRequest("Updated", "New content", List.of()));

        assertThat(response.title()).isEqualTo("Updated");
    }

    @Test
    void updateNote_notOwned_throws404() {
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.updateNote(userId, noteId, new NoteRequest("x", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteNote_success() {
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(note));

        noteService.deleteNote(userId, noteId);

        verify(noteRepository).deleteByIdAndUserId(noteId, userId);
    }

    @Test
    void deleteNote_notOwned_throws404() {
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.deleteNote(userId, noteId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(noteRepository, never()).deleteByIdAndUserId(any(), any());
    }

    private List<Note> buildNotes(UUID ownerId, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i ->
                Note.builder()
                        .id(UUID.randomUUID())
                        .userId(ownerId)
                        .title("Note " + i)
                        .content("c")
                        .tags(List.of())
                        .createdAt(Instant.now().minusSeconds(i))
                        .updatedAt(Instant.now().minusSeconds(i))
                        .build()
        ).toList();
    }
}
