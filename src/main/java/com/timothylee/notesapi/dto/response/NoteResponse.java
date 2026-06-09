package com.timothylee.notesapi.dto.response;

import com.timothylee.notesapi.model.Note;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoteResponse(
        UUID id,
        String title,
        String content,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
    public static NoteResponse from(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getTags(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
