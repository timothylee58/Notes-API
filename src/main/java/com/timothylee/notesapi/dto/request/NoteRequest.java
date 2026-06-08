package com.timothylee.notesapi.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteRequest(
        @Size(max = 255) String title,
        String content,
        @Size(max = 20) List<@Size(max = 50) String> tags
) {}
