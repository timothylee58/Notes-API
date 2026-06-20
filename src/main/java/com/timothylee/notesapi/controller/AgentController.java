package com.timothylee.notesapi.controller;

import com.timothylee.notesapi.dto.request.AgentChatRequest;
import com.timothylee.notesapi.dto.response.AgentChatResponse;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    @Operation(summary = "Chat with the notes agent", description = "Send a natural-language message; the agent may read, create, update, or delete notes on your behalf.")
    public AgentChatResponse chat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AgentChatRequest request) {
        String reply = agentService.chat(request.message(), user.getId());
        return new AgentChatResponse(reply);
    }
}
