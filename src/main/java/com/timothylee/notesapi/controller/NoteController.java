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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/notes")
@Tag(name = "Notes")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    @Operation(summary = "List notes (keyset paginated)")
    public PagedResponse<NoteResponse> getNotes(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return noteService.getNotes(user.getId(), cursor, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a note")
    public NoteResponse createNote(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NoteRequest request) {
        return noteService.createNote(user.getId(), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a note by id")
    public NoteResponse getNoteById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return noteService.getNoteById(user.getId(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a note")
    public NoteResponse updateNote(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request) {
        return noteService.updateNote(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a note")
    public void deleteNote(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        noteService.deleteNote(user.getId(), id);
    }
}
