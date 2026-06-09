package com.timothylee.notesapi.repository;

import com.timothylee.notesapi.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    @Query("SELECT n FROM Note n WHERE n.userId = :userId " +
           "AND (:cursor IS NULL OR n.createdAt < :cursor) " +
           "ORDER BY n.createdAt DESC LIMIT :limit")
    List<Note> findByUserIdWithKeyset(
            @Param("userId") UUID userId,
            @Param("cursor") Instant cursor,
            @Param("limit") int limit);

    Optional<Note> findByIdAndUserId(UUID id, UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
