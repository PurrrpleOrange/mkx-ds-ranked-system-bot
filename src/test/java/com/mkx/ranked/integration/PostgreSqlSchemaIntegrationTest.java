package com.mkx.ranked.integration;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlSchemaIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void cleanPostgresAppliesAllMigrationsAndHibernateValidatesSchema() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class
        );

        assertEquals(List.of("1", "2", "3"), versions);
        assertTrue(entityManagerFactory.isOpen());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT nextval('season_number_seq')", Long.class));
        assertEquals(
                List.of("display_name", "final_rank", "games_played", "id", "player_id", "rating", "season_id"),
                jdbcTemplate.queryForList(
                        """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'season_players'
                        ORDER BY column_name
                        """,
                        String.class
                )
        );
    }

    @Test
    void databaseRejectsTwoActiveSeasonsThroughPartialUniqueIndex() {
        jdbcTemplate.update("INSERT INTO seasons(id, season_number, name, status) VALUES (1, 1, 'One', 'ACTIVE')");

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO seasons(id, season_number, name, status) VALUES (2, 2, 'Two', 'ACTIVE')"
                )
        );

        assertTrue(rootMessage(failure).contains("uq_seasons_single_active"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM seasons WHERE status = 'ACTIVE'", Integer.class
        ));
    }

    @Test
    void databaseRejectsDuplicatePlayerParticipationWithinSeason() {
        insertSeasonAndPlayerFixture();
        jdbcTemplate.update("""
                INSERT INTO season_players(id, season_id, player_id, display_name, rating, games_played)
                VALUES (10, 1, 1, 'Scorpion', 1000, 0)
                """);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO season_players(id, season_id, player_id, display_name, rating, games_played)
                        VALUES (11, 1, 1, 'Hanzo', 1000, 0)
                        """)
        );

        assertTrue(rootMessage(failure).contains("uq_season_player"));
    }

    @Test
    void displayNameIsCaseInsensitiveUniqueWithinOneSeason() {
        insertSeasonAndPlayerFixture();
        jdbcTemplate.update("INSERT INTO players(id, discord_id, username) VALUES (2, 22, 'second')");
        jdbcTemplate.update("""
                INSERT INTO season_players(id, season_id, player_id, display_name, rating, games_played)
                VALUES (10, 1, 1, 'Scorpion', 1000, 0)
                """);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO season_players(id, season_id, player_id, display_name, rating, games_played)
                        VALUES (11, 1, 2, 'scorpion', 1000, 0)
                        """)
        );

        assertTrue(rootMessage(failure).contains("uq_season_player_display_name_ci"));
    }

    @Test
    void sameDisplayNameIsAllowedInDifferentSeasons() {
        insertSeasonAndPlayerFixture();
        jdbcTemplate.update("INSERT INTO seasons(id, season_number, name, status) VALUES (2, 2, 'Two', 'CREATED')");
        jdbcTemplate.update("""
                INSERT INTO season_players(id, season_id, player_id, display_name, rating, games_played)
                VALUES (10, 1, 1, 'Scorpion', 1000, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO season_players(id, season_id, player_id, display_name, rating, games_played)
                VALUES (11, 2, 1, 'scorpion', 1000, 0)
                """);

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM season_players WHERE lower(display_name) = 'scorpion'", Integer.class
        ));
    }

    private void insertSeasonAndPlayerFixture() {
        jdbcTemplate.update("INSERT INTO seasons(id, season_number, name, status) VALUES (1, 1, 'One', 'CREATED')");
        jdbcTemplate.update("INSERT INTO players(id, discord_id, username) VALUES (1, 11, 'first')");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
