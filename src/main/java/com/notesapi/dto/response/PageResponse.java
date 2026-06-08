package com.notesapi.dto.response;

import java.util.List;

public record PageResponse<T>(
        List<T> data,
        String nextCursor,
        boolean hasMore,
        int limit
) {}
