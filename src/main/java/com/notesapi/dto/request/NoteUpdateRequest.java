package com.notesapi.dto.request;

import jakarta.validation.constraints.Size;

public record NoteUpdateRequest(
        @Size(max = 255) String title,
        String content
) {}
