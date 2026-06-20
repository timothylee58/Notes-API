package com.timothylee.notesapi.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AgentChatRequest(@NotBlank String message) {}
