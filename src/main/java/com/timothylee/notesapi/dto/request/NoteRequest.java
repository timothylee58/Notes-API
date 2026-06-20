package com.timothylee.notesapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteRequest(
        @NotBlank @Size(max = 255) @Schema(example = "My first note") String title,
        @Schema(example = "Notes content goes here") String content,
        @Schema(example = "[\"work\", \"ideas\"]") List<String> tags
) {}
