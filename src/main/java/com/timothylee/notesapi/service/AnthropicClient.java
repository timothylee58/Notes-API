package com.timothylee.notesapi.service;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnthropicClient {

    private final com.anthropic.client.AnthropicClient sdkClient;
    private final ObjectMapper objectMapper;

    private static final String MODEL = "claude-opus-5-5";
    private static final long MAX_TOKENS = 4096L;

    private static final List<Tool> TOOLS = List.of(
            buildTool("list_notes",
                    "List notes for the current user. Supports keyset pagination via cursor.",
                    Map.of(
                            "limit", Map.of("type", "integer", "description", "Max notes to return (1-100, default 20)"),
                            "cursor", Map.of("type", "string", "description", "Pagination cursor from a previous list_notes response")),
                    List.of()),
            buildTool("get_note",
                    "Get the full content of a specific note by its ID.",
                    Map.of("id", Map.of("type", "string", "description", "The UUID of the note")),
                    List.of("id")),
            buildTool("create_note",
                    "Create a new note.",
                    Map.of(
                            "title", Map.of("type", "string", "description", "Note title"),
                            "content", Map.of("type", "string", "description", "Note body content"),
                            "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of tags")),
                    List.of("title", "content")),
            buildTool("update_note",
                    "Update an existing note.",
                    Map.of(
                            "id", Map.of("type", "string", "description", "The UUID of the note to update"),
                            "title", Map.of("type", "string", "description", "New title"),
                            "content", Map.of("type", "string", "description", "New body content"),
                            "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Updated tags")),
                    List.of("id", "title", "content")),
            buildTool("delete_note",
                    "Delete a note by its ID.",
                    Map.of("id", Map.of("type", "string", "description", "The UUID of the note to delete")),
                    List.of("id")),
            buildTool("search_notes",
                    "Full-text search across the current user's notes by title and content.",
                    Map.of(
                            "q", Map.of("type", "string", "description", "Search query string"),
                            "limit", Map.of("type", "integer", "description", "Max results to return (1-100, default 20)")),
                    List.of("q"))
    );

    private static Tool buildTool(String name, String description,
                                   Map<String, Object> properties, List<String> required) {
        Tool.InputSchema.Properties.Builder propsBuilder = Tool.InputSchema.Properties.builder();
        properties.forEach((k, v) -> propsBuilder.putAdditionalProperty(k, JsonValue.from(v)));

        return Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(propsBuilder.build())
                        .required(required)
                        .build())
                .build();
    }

    public JsonNode sendMessages(List<Map<String, Object>> messages) {
        List<MessageParam> params = convertMessages(messages);

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS);
        for (MessageParam p : params) {
            builder.addMessage(p);
        }
        for (Tool t : TOOLS) {
            builder.addTool(t);
        }

        Message response = sdkClient.messages().create(builder.build());
        return toJsonNode(response);
    }

    private List<MessageParam> convertMessages(List<Map<String, Object>> messages) {
        List<MessageParam> result = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            Object content = msg.get("content");

            if ("user".equals(role)) {
                if (content instanceof String text) {
                    result.add(MessageParam.ofUserOfContentBlockParams(
                            List.of(ContentBlockParam.ofText(
                                    TextBlockParam.builder().text(text).build()))));
                } else if (content instanceof List<?> blocks) {
                    List<ContentBlockParam> cbParams = new ArrayList<>();
                    for (Object block : blocks) {
                        if (block instanceof Map<?, ?> blockMap) {
                            String type = (String) blockMap.get("type");
                            if ("tool_result".equals(type)) {
                                cbParams.add(ContentBlockParam.ofToolResult(
                                        ToolResultBlockParam.builder()
                                                .toolUseId((String) blockMap.get("tool_use_id"))
                                                .content((String) blockMap.get("content"))
                                                .build()));
                            }
                        }
                    }
                    result.add(MessageParam.ofUserOfContentBlockParams(cbParams));
                }
            } else if ("assistant".equals(role)) {
                if (content instanceof List<?> blocks) {
                    List<ContentBlockParam> cbParams = new ArrayList<>();
                    for (Object block : blocks) {
                        if (block instanceof Map<?, ?> blockMap) {
                            String type = (String) blockMap.get("type");
                            if ("text".equals(type)) {
                                cbParams.add(ContentBlockParam.ofText(
                                        TextBlockParam.builder()
                                                .text((String) blockMap.get("text"))
                                                .build()));
                            } else if ("tool_use".equals(type)) {
                                cbParams.add(ContentBlockParam.ofToolUse(
                                        ToolUseBlockParam.builder()
                                                .id((String) blockMap.get("id"))
                                                .name((String) blockMap.get("name"))
                                                .input(JsonValue.from(blockMap.get("input")))
                                                .build()));
                            }
                        }
                    }
                    result.add(MessageParam.ofAssistantOfContentBlockParams(cbParams));
                }
            }
        }
        return result;
    }

    private JsonNode toJsonNode(Message message) {
        ObjectNode root = objectMapper.createObjectNode();

        String stopReason = message.stopReason()
                .map(sr -> sr.toString().toLowerCase())
                .orElse("end_turn");
        // The SDK StopReason enum values: END_TURN, MAX_TOKENS, STOP_SEQUENCE, TOOL_USE
        // .toString() on the enum gives e.g. "END_TURN" — normalise to snake_case API wire format
        root.put("stop_reason", toWireStopReason(stopReason));

        ArrayNode contentArray = root.putArray("content");
        for (ContentBlock block : message.content()) {
            ObjectNode blockNode = objectMapper.createObjectNode();

            block.text().ifPresent(tb -> {
                blockNode.put("type", "text");
                blockNode.put("text", tb.text());
            });

            block.toolUse().ifPresent(tu -> {
                blockNode.put("type", "tool_use");
                blockNode.put("id", tu.id());
                blockNode.put("name", tu.name());
                blockNode.set("input", tu._input().convert(JsonNode.class));
            });

            if (!blockNode.isEmpty()) {
                contentArray.add(blockNode);
            }
        }

        return root;
    }

    private String toWireStopReason(String sdkEnumStr) {
        return switch (sdkEnumStr.toUpperCase()) {
            case "END_TURN" -> "end_turn";
            case "TOOL_USE" -> "tool_use";
            case "MAX_TOKENS" -> "max_tokens";
            case "STOP_SEQUENCE" -> "stop_sequence";
            default -> sdkEnumStr.toLowerCase();
        };
    }
}
