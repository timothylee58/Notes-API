package com.timothylee.notesapi.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timothylee.notesapi.model.Note;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KeysetPaginationHelper {

    private final ObjectMapper objectMapper;

    public String encodeCursor(Note note) {
        try {
            String json = objectMapper.writeValueAsString(
                    Map.of("createdAt", note.getCreatedAt().toString(),
                           "id", note.getId().toString()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    @SuppressWarnings("unchecked")
    public DecodedCursor decodeCursor(String cursor) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            Map<String, String> raw = objectMapper.readValue(bytes, Map.class);
            return new DecodedCursor(Instant.parse(raw.get("createdAt")), raw.get("id"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    public record DecodedCursor(Instant createdAt, String id) {}
}
