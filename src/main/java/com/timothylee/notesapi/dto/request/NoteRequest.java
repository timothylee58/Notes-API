package com.timothylee.notesapi.dto.request;

import jakarta.validation.constraints.Size;

public record NoteRequest(
        @Size(max = 255) String title,
        String content
) {}
