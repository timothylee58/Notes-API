ALTER TABLE notes
    ADD COLUMN fts_vector tsvector
        GENERATED ALWAYS AS (
            to_tsvector('english', coalesce(title, '') || ' ' || coalesce(content, ''))
        ) STORED;

CREATE INDEX idx_notes_fts ON notes USING gin(fts_vector);
