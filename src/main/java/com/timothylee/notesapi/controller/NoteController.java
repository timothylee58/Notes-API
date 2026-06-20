package com.timothylee.notesapi.controller;

import com.timothylee.notesapi.dto.request.NoteRequest;
import com.timothylee.notesapi.dto.response.NoteResponse;
import com.timothylee.notesapi.dto.response.PagedResponse;
import com.timothylee.notesapi.model.User;
import com.timothylee.notesapi.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(summary = "List notes (keyset paginated)", description = "Returns up to `limit` notes for the authenticated user, ordered by `created_at DESC`. Pass `cursor` from the previous response to get the next page.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of notes"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public PagedResponse<NoteResponse> getNotes(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return noteService.getNotes(user.getId(), cursor, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a note")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Note created"),
        @ApiResponse(responseCode = "422", description = "Validation failed", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public NoteResponse createNote(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NoteRequest request) {
        return noteService.createNote(user.getId(), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a note by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Note found"),
        @ApiResponse(responseCode = "404", description = "Not found or not owned", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public NoteResponse getNoteById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return noteService.getNoteById(user.getId(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a note")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Note updated"),
        @ApiResponse(responseCode = "404", description = "Not found or not owned", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "422", description = "Validation failed", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public NoteResponse updateNote(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request) {
        return noteService.updateNote(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a note")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Note deleted"),
        @ApiResponse(responseCode = "404", description = "Not found or not owned", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    public void deleteNote(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        noteService.deleteNote(user.getId(), id);
    }
}
