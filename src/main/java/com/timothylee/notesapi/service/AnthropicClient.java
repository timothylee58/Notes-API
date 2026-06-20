package com.timothylee.notesapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnthropicClient {

    @Qualifier("anthropicRestClient")
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String MODEL = "claude-opus-4-8";
    private static final int MAX_TOKENS = 4096;

    private static final List<Map<String, Object>> TOOLS = List.of(
            tool("list_notes", "List notes for the current user. Supports keyset pagination via cursor.",
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "limit", Map.of("type", "integer", "description", "Max notes to return (1-100, default 20)"),
                                    "cursor", Map.of("type", "string", "description", "Pagination cursor from a previous list_notes response")),
                            "required", List.of())),
            tool("get_note", "Get the full content of a specific note by its ID.",
                    Map.of("type", "object",
                            "properties", Map.of("id", Map.of("type", "string", "description", "The UUID of the note")),
                            "required", List.of("id"))),
            tool("create_note", "Create a new note.",
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "title", Map.of("type", "string", "description", "Note title"),
                                    "content", Map.of("type", "string", "description", "Note body content"),
                                    "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of tags")),
                            "required", List.of("title", "content"))),
            tool("update_note", "Update an existing note.",
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "id", Map.of("type", "string", "description", "The UUID of the note to update"),
                                    "title", Map.of("type", "string", "description", "New title"),
                                    "content", Map.of("type", "string", "description", "New body content"),
                                    "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Updated tags")),
                            "required", List.of("id", "title", "content"))),
            tool("delete_note", "Delete a note by its ID.",
                    Map.of("type", "object",
                            "properties", Map.of("id", Map.of("type", "string", "description", "The UUID of the note to delete")),
                            "required", List.of("id")))
    );

    private static Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        return Map.of("name", name, "description", description, "input_schema", inputSchema);
    }

    public JsonNode sendMessages(List<Map<String, Object>> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.set("messages", objectMapper.valueToTree(messages));
        body.set("tools", objectMapper.valueToTree(TOOLS));

        String response = restClient.post()
                .uri("/v1/messages")
                .body(body.toString())
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Anthropic response", e);
        }
    }
}
