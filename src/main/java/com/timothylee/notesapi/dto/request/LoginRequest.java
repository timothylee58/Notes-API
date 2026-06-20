package com.timothylee.notesapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email @NotBlank @Schema(example = "alice@example.com") String email,
        @NotBlank @Schema(example = "p@ssw0rd!") String password
) {}
