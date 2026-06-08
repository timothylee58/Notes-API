package com.timothylee.notesapi.util;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Base64;

@Component
public class KeysetPaginationHelper {

    public String encodeCursor(Instant timestamp) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(timestamp.toString().getBytes());
    }

    public Instant decodeCursor(String cursor) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            return Instant.parse(new String(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pagination cursor", e);
        }
    }
}
