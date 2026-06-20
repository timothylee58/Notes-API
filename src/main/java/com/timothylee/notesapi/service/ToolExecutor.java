package com.timothylee.notesapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timothylee.notesapi.dto.request.NoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ToolExecutor {

    private final NoteService noteService;
    private final ObjectMapper objectMapper;

    // userId is ALWAYS from Spring SecurityContext, never from LLM output
    public String execute(String toolName, Map<String, Object> input, UUID userId) {
        return switch (toolName) {
            case "list_notes" -> {
                var page = noteService.getNotes(userId, null, 20);
                yield toJson(page.data());
            }
            case "get_note" -> {
                var noteId = parseUUID(input, "id");
                yield toJson(noteService.getNoteById(userId, noteId));
            }
            case "create_note" -> {
                var req = buildNoteRequest(input);
                yield toJson(noteService.createNote(userId, req));
            }
            case "update_note" -> {
                var noteId = parseUUID(input, "id");
                var req = buildNoteRequest(input);
                yield toJson(noteService.updateNote(userId, noteId, req));
            }
            case "delete_note" -> {
                var noteId = parseUUID(input, "id");
                noteService.deleteNote(userId, noteId);
                yield "deleted";
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    private UUID parseUUID(Map<String, Object> input, String key) {
        Object val = input.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required field: " + key);
        return UUID.fromString(val.toString());
    }

    @SuppressWarnings("unchecked")
    private NoteRequest buildNoteRequest(Map<String, Object> input) {
        String title = (String) input.get("title");
        String content = (String) input.get("content");
        Object rawTags = input.get("tags");
        List<String> tags = rawTags instanceof List<?> l
                ? l.stream().map(Object::toString).toList()
                : List.of();
        return new NoteRequest(title, content, tags);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize tool result", e);
        }
    }
}
