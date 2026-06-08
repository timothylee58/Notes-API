package com.notesapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteCreateRequest(
        @NotBlank @Size(max = 255) String title,
        String content
) {}
