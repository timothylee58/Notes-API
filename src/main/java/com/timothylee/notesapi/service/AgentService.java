package com.timothylee.notesapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final int MAX_ITERATIONS = 5;
    private static final String FALLBACK_REPLY = "I was unable to complete your request within the allowed steps.";

    private final AnthropicClient anthropicClient;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public String chat(String userMessage, UUID userId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            JsonNode response = anthropicClient.sendMessages(messages);
            String stopReason = response.path("stop_reason").asText();
            JsonNode contentBlocks = response.path("content");

            if ("end_turn".equals(stopReason)) {
                for (JsonNode block : contentBlocks) {
                    if ("text".equals(block.path("type").asText())) {
                        return block.path("text").asText();
                    }
                }
                return FALLBACK_REPLY;
            }

            if ("tool_use".equals(stopReason)) {
                // Add assistant's response to messages
                messages.add(Map.of("role", "assistant", "content", toList(contentBlocks)));

                // Execute all tool calls and collect results
                List<Map<String, Object>> toolResults = new ArrayList<>();
                for (JsonNode block : contentBlocks) {
                    if (!"tool_use".equals(block.path("type").asText())) continue;

                    String toolUseId = block.path("id").asText();
                    String toolName = block.path("name").asText();
                    Map<String, Object> toolInput = objectMapper.convertValue(
                            block.path("input"), new TypeReference<>() {});

                    String result;
                    try {
                        result = toolExecutor.execute(toolName, toolInput, userId);
                    } catch (Exception e) {
                        log.warn("Tool execution failed for '{}': {}", toolName, e.getMessage());
                        result = "Error: " + e.getMessage();
                    }

                    toolResults.add(Map.of(
                            "type", "tool_result",
                            "tool_use_id", toolUseId,
                            "content", result
                    ));
                }

                messages.add(Map.of("role", "user", "content", toolResults));
            } else {
                log.warn("Unexpected stop_reason: {}", stopReason);
                break;
            }
        }

        return FALLBACK_REPLY;
    }

    private List<Object> toList(JsonNode arrayNode) {
        List<Object> list = new ArrayList<>();
        arrayNode.forEach(node -> list.add(objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {})));
        return list;
    }
}
