package com.timothylee.notesapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank @Schema(example = "alice@example.com") String email,
        @NotBlank @Size(min = 8, max = 72) @Schema(example = "p@ssw0rd!") String password,
        @NotBlank @Schema(example = "Alice Tan") String fullName
) {}
