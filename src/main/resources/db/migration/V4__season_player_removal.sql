ALTER TABLE season_players
    ADD COLUMN removed_at TIMESTAMP,
    ADD COLUMN removed_by BIGINT,
    ADD CONSTRAINT chk_season_player_removal CHECK (
        (removed_at IS NULL AND removed_by IS NULL)
        OR (removed_at IS NOT NULL AND removed_by IS NOT NULL AND removed_by > 0)
    );
