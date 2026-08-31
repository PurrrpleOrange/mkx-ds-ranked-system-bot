CREATE TABLE players (
                         id BIGSERIAL PRIMARY KEY,
                         discord_id BIGINT UNIQUE,
                         username VARCHAR(100) NOT NULL,
                         display_name VARCHAR(100) NOT NULL
);

CREATE TABLE seasons (
                         id BIGSERIAL PRIMARY KEY,
                         season_number INTEGER NOT NULL UNIQUE,
                         name VARCHAR(100) NOT NULL,
                         status VARCHAR(20) NOT NULL,
                         start_date TIMESTAMP,
                         planned_end_date TIMESTAMP,
                         end_date TIMESTAMP,

                         CONSTRAINT chk_season_status
                             CHECK (status IN ('CREATED', 'ACTIVE', 'FINISHED'))
);

CREATE TABLE season_players (
                                id BIGSERIAL PRIMARY KEY,
                                season_id BIGINT NOT NULL,
                                player_id BIGINT NOT NULL,

                                rating INTEGER NOT NULL,
                                games_played INTEGER NOT NULL DEFAULT 0,
                                final_rank INTEGER,

                                CONSTRAINT fk_season_player_season
                                    FOREIGN KEY (season_id)
                                        REFERENCES seasons(id),

                                CONSTRAINT fk_season_player_player
                                    FOREIGN KEY (player_id)
                                        REFERENCES players(id),

                                CONSTRAINT uq_season_player
                                    UNIQUE (season_id, player_id),

                                CONSTRAINT chk_rating
                                    CHECK (rating >= 0),

                                CONSTRAINT chk_games_played
                                    CHECK (games_played >= 0),

                                CONSTRAINT chk_final_rank
                                    CHECK (final_rank IS NULL OR final_rank > 0)
);

CREATE TABLE matches (
                         id BIGSERIAL PRIMARY KEY,

                         season_id BIGINT NOT NULL,
                         winner_id BIGINT NOT NULL,
                         loser_id BIGINT NOT NULL,

                         winner_score INTEGER NOT NULL,
                         loser_score INTEGER NOT NULL,

                         delta_winner INTEGER NOT NULL,
                         delta_loser INTEGER NOT NULL,

                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT fk_match_season
                             FOREIGN KEY (season_id)
                                 REFERENCES seasons(id),

                         CONSTRAINT fk_match_winner
                             FOREIGN KEY (winner_id)
                                 REFERENCES season_players(id),

                         CONSTRAINT fk_match_loser
                             FOREIGN KEY (loser_id)
                                 REFERENCES season_players(id),

                         CONSTRAINT chk_different_players
                             CHECK (winner_id <> loser_id),

                         CONSTRAINT chk_match_score
                             CHECK (
                                 winner_score >= 0
                                     AND loser_score >= 0
                                     AND winner_score > loser_score
                                 )
);

CREATE INDEX idx_season_players_season_id
    ON season_players(season_id);

CREATE INDEX idx_season_players_player_id
    ON season_players(player_id);

CREATE INDEX idx_matches_season_id
    ON matches(season_id);

CREATE INDEX idx_matches_winner_id
    ON matches(winner_id);

CREATE INDEX idx_matches_loser_id
    ON matches(loser_id);