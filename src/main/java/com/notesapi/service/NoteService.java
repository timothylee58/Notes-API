package com.notesapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapi.dto.request.NoteCreateRequest;
import com.notesapi.dto.request.NoteUpdateRequest;
import com.notesapi.dto.response.NoteResponse;
import com.notesapi.dto.response.PageResponse;
import com.notesapi.entity.Note;
import com.notesapi.entity.User;
import com.notesapi.exception.NoteNotFoundException;
import com.notesapi.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public NoteResponse create(User user, NoteCreateRequest request) {
        var note = Note.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .build();
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public NoteResponse getById(User user, UUID noteId) {
        return NoteResponse.from(findOwned(user, noteId));
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteResponse> list(User user, String cursor, int limit) {
        int fetch = limit + 1; // fetch one extra to detect hasMore
        List<Note> results;

        if (cursor == null || cursor.isBlank()) {
            results = noteRepository.findFirstPage(user.getId(), fetch);
        } else {
            var decoded = decodeCursor(cursor);
            results = noteRepository.findNextPage(
                    user.getId(),
                    (Instant) decoded.get("createdAt"),
                    (String) decoded.get("id"),
                    fetch);
        }

        boolean hasMore = results.size() > limit;
        List<Note> page = hasMore ? results.subList(0, limit) : results;

        String nextCursor = (hasMore && !page.isEmpty())
                ? encodeCursor(page.get(page.size() - 1))
                : null;

        return new PageResponse<>(page.stream().map(NoteResponse::from).toList(), nextCursor, hasMore, limit);
    }

    @Transactional
    public NoteResponse update(User user, UUID noteId, NoteUpdateRequest request) {
        var note = findOwned(user, noteId);
        if (request.title() != null) note.setTitle(request.title());
        if (request.content() != null) note.setContent(request.content());
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional
    public void delete(User user, UUID noteId) {
        var note = findOwned(user, noteId);
        note.setDeletedAt(Instant.now());
        noteRepository.save(note);
    }

    private Note findOwned(User user, UUID noteId) {
        return noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, user.getId())
                .orElseThrow(() -> new NoteNotFoundException(noteId));
    }

    private String encodeCursor(Note note) {
        try {
            String json = objectMapper.writeValueAsString(
                    Map.of("createdAt", note.getCreatedAt().toString(), "id", note.getId().toString()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeCursor(String cursor) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            Map<String, String> raw = objectMapper.readValue(bytes, Map.class);
            return Map.of("createdAt", Instant.parse(raw.get("createdAt")), "id", raw.get("id"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }
}
