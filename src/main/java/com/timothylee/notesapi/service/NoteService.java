package com.timothylee.notesapi.service;

import com.timothylee.notesapi.dto.request.NoteRequest;
import com.timothylee.notesapi.dto.response.NoteResponse;
import com.timothylee.notesapi.dto.response.PagedResponse;
import com.timothylee.notesapi.exception.ResourceNotFoundException;
import com.timothylee.notesapi.model.Note;
import com.timothylee.notesapi.repository.NoteRepository;
import com.timothylee.notesapi.util.KeysetPaginationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final KeysetPaginationHelper paginationHelper;

    @Transactional(readOnly = true)
    public PagedResponse<NoteResponse> getNotes(UUID userId, String cursor, int limit) {
        Instant cursorInstant = (cursor != null && !cursor.isBlank())
                ? paginationHelper.decodeCursor(cursor)
                : null;

        int fetch = limit + 1;
        List<Note> results = noteRepository.findByUserIdWithKeyset(userId, cursorInstant, fetch);

        boolean hasMore = results.size() > limit;
        List<Note> page = hasMore ? results.subList(0, limit) : results;
        String nextCursor = (hasMore && !page.isEmpty())
                ? paginationHelper.encodeCursor(page.get(page.size() - 1).getCreatedAt())
                : null;

        return new PagedResponse<>(page.stream().map(NoteResponse::from).toList(), nextCursor, hasMore, limit);
    }

    @Transactional
    public NoteResponse createNote(UUID userId, NoteRequest request) {
        var note = Note.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .tags(request.tags() != null ? request.tags() : List.of())
                .build();
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public NoteResponse getNoteById(UUID userId, UUID noteId) {
        return NoteResponse.from(findOwned(userId, noteId));
    }

    @Transactional
    public NoteResponse updateNote(UUID userId, UUID noteId, NoteRequest request) {
        var note = findOwned(userId, noteId);
        note.setTitle(request.title());
        note.setContent(request.content());
        if (request.tags() != null) note.setTags(request.tags());
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(UUID userId, UUID noteId) {
        findOwned(userId, noteId);
        noteRepository.deleteByIdAndUserId(noteId, userId);
    }

    private Note findOwned(UUID userId, UUID noteId) {
        return noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found: " + noteId));
    }
}
