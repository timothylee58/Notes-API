package com.timothylee.notesapi.repository;

import com.timothylee.notesapi.model.Note;
import com.timothylee.notesapi.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NoteRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notestest")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired NoteRepository noteRepository;
    @Autowired UserRepository userRepository;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .email("alice@example.com").password("hashed").fullName("Alice").build());
        User other = userRepository.save(User.builder()
                .email("bob@example.com").password("hashed").fullName("Bob").build());
        userId = user.getId();
        otherUserId = other.getId();

        // Insert notes with distinct timestamps to ensure ordering
        for (int i = 0; i < 5; i++) {
            noteRepository.save(Note.builder()
                    .userId(userId).title("Alice Note " + i)
                    .content("c").tags(List.of()).build());
            // small delay to ensure created_at ordering
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        }
        noteRepository.save(Note.builder()
                .userId(otherUserId).title("Bob Note")
                .content("c").tags(List.of()).build());
    }

    @Test
    void testKeyset_pagination_firstPage() {
        List<Note> page = noteRepository.findByUserIdWithKeyset(userId, null, 3);

        assertThat(page).hasSize(3);
        assertThat(page).allMatch(n -> n.getUserId().equals(userId));
        // ordered by created_at DESC — most recent first
        assertThat(page.get(0).getCreatedAt()).isAfterOrEqualTo(page.get(1).getCreatedAt());
    }

    @Test
    void testKeyset_pagination_secondPage_with_cursor() {
        List<Note> firstPage = noteRepository.findByUserIdWithKeyset(userId, null, 3);
        Instant cursor = firstPage.get(firstPage.size() - 1).getCreatedAt();

        List<Note> secondPage = noteRepository.findByUserIdWithKeyset(userId, cursor, 3);

        assertThat(secondPage).isNotEmpty();
        assertThat(secondPage).allMatch(n -> !n.getCreatedAt().isAfter(cursor));
        // No overlap with first page
        var firstIds = firstPage.stream().map(Note::getId).toList();
        assertThat(secondPage).noneMatch(n -> firstIds.contains(n.getId()));
    }

    @Test
    void testKeyset_onlyReturnsOwnNotes() {
        List<Note> notes = noteRepository.findByUserIdWithKeyset(userId, null, 100);

        assertThat(notes).isNotEmpty();
        assertThat(notes).allMatch(n -> n.getUserId().equals(userId));
        assertThat(notes).noneMatch(n -> n.getUserId().equals(otherUserId));
    }
}
