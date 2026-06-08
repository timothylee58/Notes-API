package com.timothylee.notesapi.service;

import com.timothylee.notesapi.dto.request.NoteRequest;
import com.timothylee.notesapi.dto.response.NoteResponse;
import com.timothylee.notesapi.dto.response.PagedResponse;
import com.timothylee.notesapi.exception.ResourceNotFoundException;
import com.timothylee.notesapi.model.Note;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.repository.NoteRepository;
import com.timothylee.notesapi.util.KeysetPaginationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final KeysetPaginationHelper paginationHelper;

    @Transactional
    public NoteResponse create(User user, NoteRequest request) {
        var note = Note.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .tags(request.tags())
                .build();
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public NoteResponse getById(User user, UUID noteId) {
        return NoteResponse.from(findOwned(user, noteId));
    }

    @Transactional(readOnly = true)
    public PagedResponse<NoteResponse> list(User user, String cursor, int limit) {
        int fetch = limit + 1;
        List<Note> results;

        if (cursor == null || cursor.isBlank()) {
            results = noteRepository.findFirstPage(user.getId(), fetch);
        } else {
            var decoded = paginationHelper.decodeCursor(cursor);
            results = noteRepository.findNextPage(user.getId(), decoded.createdAt(), decoded.id(), fetch);
        }

        boolean hasMore = results.size() > limit;
        List<Note> page = hasMore ? results.subList(0, limit) : results;
        String nextCursor = (hasMore && !page.isEmpty())
                ? paginationHelper.encodeCursor(page.get(page.size() - 1))
                : null;

        return new PagedResponse<>(page.stream().map(NoteResponse::from).toList(), nextCursor, hasMore, limit);
    }

    @Transactional
    public NoteResponse update(User user, UUID noteId, NoteRequest request) {
        var note = findOwned(user, noteId);
        if (request.title() != null) note.setTitle(request.title());
        if (request.content() != null) note.setContent(request.content());
        if (request.tags() != null) note.setTags(request.tags());
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
                .orElseThrow(() -> new ResourceNotFoundException("Note not found: " + noteId));
    }
}
