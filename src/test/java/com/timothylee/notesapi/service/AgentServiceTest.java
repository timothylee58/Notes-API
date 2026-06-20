package com.timothylee.notesapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock AnthropicClient anthropicClient;
    @Mock ToolExecutor toolExecutor;
    @InjectMocks AgentService agentService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID userId;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(anthropicClient, toolExecutor, objectMapper);
        userId = UUID.randomUUID();
    }

    @Test
    void single_turn_text_response_returns_text() {
        var response = buildTextResponse("Hello! How can I help?");
        when(anthropicClient.sendMessages(any())).thenReturn(response);

        String reply = agentService.chat("hi", userId);

        assertThat(reply).isEqualTo("Hello! How can I help?");
        verify(anthropicClient, times(1)).sendMessages(any());
    }

    @Test
    void tool_use_then_text_dispatches_tool_and_returns_final_reply() {
        String toolUseId = "toolu_01";
        var toolUseResponse = buildToolUseResponse(toolUseId, "list_notes", Map.of());
        var textResponse = buildTextResponse("You have 3 notes.");

        when(anthropicClient.sendMessages(any()))
                .thenReturn(toolUseResponse)
                .thenReturn(textResponse);
        when(toolExecutor.execute(eq("list_notes"), any(), eq(userId))).thenReturn("[]");

        String reply = agentService.chat("list my notes", userId);

        assertThat(reply).isEqualTo("You have 3 notes.");
        verify(toolExecutor).execute(eq("list_notes"), any(), eq(userId));
        verify(anthropicClient, times(2)).sendMessages(any());
    }

    @Test
    void max_iterations_cap_returns_fallback() {
        String toolUseId = "toolu_loop";
        var toolUseResponse = buildToolUseResponse(toolUseId, "list_notes", Map.of());
        when(anthropicClient.sendMessages(any())).thenReturn(toolUseResponse);
        when(toolExecutor.execute(anyString(), any(), any())).thenReturn("[]");

        String reply = agentService.chat("loop forever", userId);

        assertThat(reply).isEqualTo("I was unable to complete your request within the allowed steps.");
        verify(anthropicClient, times(5)).sendMessages(any());
    }

    private com.fasterxml.jackson.databind.JsonNode buildTextResponse(String text) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("stop_reason", "end_turn");
        var content = root.putArray("content");
        var block = content.addObject();
        block.put("type", "text");
        block.put("text", text);
        return root;
    }

    private com.fasterxml.jackson.databind.JsonNode buildToolUseResponse(String id, String name, Map<String, Object> input) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("stop_reason", "tool_use");
        var content = root.putArray("content");
        var block = content.addObject();
        block.put("type", "tool_use");
        block.put("id", id);
        block.put("name", name);
        block.set("input", objectMapper.valueToTree(input));
        return root;
    }
}
