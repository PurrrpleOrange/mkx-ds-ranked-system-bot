ALTER TABLE season_players
    ADD COLUMN display_name VARCHAR(100);

UPDATE season_players sp
SET display_name = p.display_name
FROM players p
WHERE p.id = sp.player_id;

ALTER TABLE season_players
    ALTER COLUMN display_name SET NOT NULL;

CREATE UNIQUE INDEX uq_season_player_display_name_ci
    ON season_players (season_id, LOWER(display_name));

ALTER TABLE players
    DROP COLUMN display_name;
