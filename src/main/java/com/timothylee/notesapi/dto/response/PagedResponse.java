package com.timothylee.notesapi.dto.response;

import java.util.List;

public record PagedResponse<T>(
        List<T> data,
        String nextCursor,
        boolean hasMore,
        int limit
) {}
