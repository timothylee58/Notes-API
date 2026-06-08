package com.timothylee.notesapi.controller;

import com.timothylee.notesapi.dto.request.NoteRequest;
import com.timothylee.notesapi.dto.response.NoteResponse;
import com.timothylee.notesapi.dto.response.PagedResponse;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notes")
@Tag(name = "Notes")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    @Operation(summary = "List notes (keyset paginated)")
    public PagedResponse<NoteResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return noteService.list(user, cursor, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a note")
    public NoteResponse create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NoteRequest request) {
        return noteService.create(user, request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a note by id")
    public NoteResponse getById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return noteService.getById(user, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full update of a note")
    public NoteResponse update(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request) {
        return noteService.update(user, id, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partial update of a note")
    public NoteResponse patch(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestBody NoteRequest request) {
        return noteService.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a note")
    public void delete(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        noteService.delete(user, id);
    }
}
