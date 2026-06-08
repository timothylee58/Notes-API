package com.notesapi.repository;

import com.notesapi.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    Optional<Note> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /**
     * Keyset pagination: first page (no cursor).
     * Index on (user_id, created_at DESC, id DESC) makes this an index seek.
     */
    @Query(value = """
            SELECT * FROM notes
            WHERE user_id = :userId
              AND deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Note> findFirstPage(@Param("userId") UUID userId, @Param("limit") int limit);

    /**
     * Keyset pagination: subsequent pages after cursor position.
     */
    @Query(value = """
            SELECT * FROM notes
            WHERE user_id = :userId
              AND deleted_at IS NULL
              AND (created_at, id) < (:cursorCreatedAt, :cursorId::uuid)
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Note> findNextPage(
            @Param("userId") UUID userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit);
}
