package com.timothylee.notesapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteRequest(
        @NotBlank @Size(max = 255) String title,
        String content,
        List<String> tags
) {}
