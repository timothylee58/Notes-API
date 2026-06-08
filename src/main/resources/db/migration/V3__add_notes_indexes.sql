-- Supports keyset pagination: WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at DESC, id DESC
CREATE INDEX idx_notes_user_keyset
    ON notes (user_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
