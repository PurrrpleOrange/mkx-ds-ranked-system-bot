package com.mkx.ranked.integration;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.InvalidMatchException;
import com.mkx.ranked.exception.SeasonNotActiveException;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import com.mkx.ranked.repository.SeasonRepository;
import com.mkx.ranked.service.MatchService;
import com.mkx.ranked.service.RegistrationService;
import com.mkx.ranked.service.SeasonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    SeasonService seasonService;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    MatchService matchService;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    SeasonPlayerRepository seasonPlayerRepository;

    @Autowired
    MatchRepository matchRepository;

    @Test
    void concurrentMatchesSharingPlayerHaveNoLostUpdate() throws Exception {
        SeasonDto season = createSeasonWithPlayers(11L, 22L, 33L);

        RaceResult<MatchResult, MatchResult> race = race(
                () -> matchService.processMatchResult(11L, 22L, 5, 1),
                () -> matchService.processMatchResult(11L, 33L, 5, 3)
        );

        assertNull(race.first().failure());
        assertNull(race.second().failure());
        assertEquals(2, matchRepository.count());
        assertParticipantStateMatchesPersistedDeltas(season.id(), 11L, 2);
        assertParticipantStateMatchesPersistedDeltas(season.id(), 22L, 1);
        assertParticipantStateMatchesPersistedDeltas(season.id(), 33L, 1);
    }

    @Test
    void matchVersusFinishEitherIncludesMatchInFinalRanksOrRejectsIt() throws Exception {
        SeasonDto season = createSeasonWithPlayers(11L, 22L);

        RaceResult<MatchResult, SeasonDto> race = race(
                () -> matchService.processMatchResult(11L, 22L, 5, 2),
                seasonService::finishActiveSeason
        );

        assertNull(race.second().failure());
        assertFinishedAndRanksMatchCurrentStandings(season.id());
        long matches = matchRepository.count();
        if (race.first().failure() == null) {
            assertEquals(1, matches);
            assertEquals(1, participation(season.id(), 11L).getGamesPlayed());
            assertEquals(1, participation(season.id(), 22L).getGamesPlayed());
        } else {
            assertInstanceOf(SeasonNotActiveException.class, race.first().failure());
            assertEquals(0, matches);
            assertEquals(0, participation(season.id(), 11L).getGamesPlayed());
            assertEquals(0, participation(season.id(), 22L).getGamesPlayed());
        }
    }

    @Test
    void registrationVersusFinishCannotAddParticipantAfterFinalRanks() throws Exception {
        SeasonDto season = createSeasonWithPlayers(11L);

        RaceResult<RegistrationResultDto, SeasonDto> race = race(
                () -> registrationService.register(22L, "discord-22", "Late Player"),
                seasonService::finishActiveSeason
        );

        assertNull(race.second().failure());
        assertFinishedAndRanksMatchCurrentStandings(season.id());
        long participants = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM season_players WHERE season_id = ?", Long.class, season.id()
        );
        long missingRanks = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM season_players WHERE season_id = ? AND final_rank IS NULL",
                Long.class,
                season.id()
        );
        assertEquals(0, missingRanks);
        if (race.first().failure() == null) {
            assertEquals(2, participants);
        } else {
            assertInstanceOf(SeasonNotActiveException.class, race.first().failure());
            assertEquals(1, participants);
        }
    }

    @Test
    void concurrentCaseInsensitiveDisplayNameRegistrationHasExactlyOneWinner() throws Exception {
        SeasonDto season = createSeasonWithPlayers();

        RaceResult<RegistrationResultDto, RegistrationResultDto> race = race(
                () -> registrationService.register(11L, "discord-11", "Scorpion"),
                () -> registrationService.register(22L, "discord-22", "scorpion")
        );

        long successes = java.util.stream.Stream.of(race.first(), race.second())
                .filter(outcome -> outcome.failure() == null)
                .count();
        List<Throwable> failures = java.util.stream.Stream.of(race.first(), race.second())
                .map(Outcome::failure)
                .filter(java.util.Objects::nonNull)
                .toList();
        assertEquals(1, successes);
        assertEquals(1, failures.size());
        assertInstanceOf(BusinessException.class, failures.get(0));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM season_players WHERE season_id = ?", Long.class, season.id()
        ));
    }

    @Test
    void concurrentRegistrationOfSameDiscordIdentityHasExactlyOneWinner() throws Exception {
        SeasonDto season = createSeasonWithPlayers();

        RaceResult<RegistrationResultDto, RegistrationResultDto> race = race(
                () -> registrationService.register(11L, "discord-11", "Scorpion"),
                () -> registrationService.register(11L, "discord-11", "Sub-Zero")
        );

        long successes = java.util.stream.Stream.of(race.first(), race.second())
                .filter(outcome -> outcome.failure() == null)
                .count();
        List<Throwable> failures = java.util.stream.Stream.of(race.first(), race.second())
                .map(Outcome::failure)
                .filter(java.util.Objects::nonNull)
                .toList();
        assertEquals(1, successes);
        assertEquals(1, failures.size());
        assertInstanceOf(BusinessException.class, failures.get(0));
        assertEquals(1L, playerRepository.count());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM season_players WHERE season_id = ?", Long.class, season.id()
        ));
    }

    @Test
    void rollbackVersusFinishCannotMutateFinishedStandingsAfterSnapshot() throws Exception {
        SeasonDto season = createSeasonWithPlayers(11L, 22L);
        MatchResult match = matchService.processMatchResult(11L, 22L, 5, 2);
        int winnerAfterMatch = participation(season.id(), 11L).getRating();
        int loserAfterMatch = participation(season.id(), 22L).getRating();

        RaceResult<Void, SeasonDto> race = race(
                () -> {
                    matchService.revertMatch(match.matchId());
                    return null;
                },
                seasonService::finishActiveSeason
        );

        assertNull(race.second().failure());
        assertFinishedAndRanksMatchCurrentStandings(season.id());
        if (race.first().failure() == null) {
            assertEquals(0, matchRepository.count());
            assertEquals(1000, participation(season.id(), 11L).getRating());
            assertEquals(1000, participation(season.id(), 22L).getRating());
            assertEquals(0, participation(season.id(), 11L).getGamesPlayed());
            assertEquals(0, participation(season.id(), 22L).getGamesPlayed());
        } else {
            assertTrue(race.first().failure() instanceof InvalidMatchException
                    || race.first().failure() instanceof SeasonNotActiveException);
            assertEquals(1, matchRepository.count());
            assertEquals(winnerAfterMatch, participation(season.id(), 11L).getRating());
            assertEquals(loserAfterMatch, participation(season.id(), 22L).getRating());
            assertEquals(1, participation(season.id(), 11L).getGamesPlayed());
            assertEquals(1, participation(season.id(), 22L).getGamesPlayed());
        }
    }

    private SeasonDto createSeasonWithPlayers(long... discordIds) {
        SeasonDto created = seasonService.createNewSeason("Concurrent", null);
        SeasonDto active = seasonService.activateSeason(created.id());
        for (long discordId : discordIds) {
            registrationService.register(discordId, "discord-" + discordId, "Player " + discordId);
        }
        return active;
    }

    private SeasonPlayerEntity participation(long seasonId, long discordId) {
        SeasonEntity season = seasonRepository.findById(seasonId).orElseThrow();
        return seasonPlayerRepository.findBySeasonAndPlayer(
                season,
                playerRepository.findByDiscordId(discordId).orElseThrow()
        ).orElseThrow();
    }

    private void assertParticipantStateMatchesPersistedDeltas(long seasonId, long discordId, int gamesPlayed) {
        SeasonPlayerEntity participant = participation(seasonId, discordId);
        Integer delta = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(sum(
                    CASE
                        WHEN winner_id = ? THEN delta_winner
                        WHEN loser_id = ? THEN delta_loser
                        ELSE 0
                    END
                ), 0)
                FROM matches
                WHERE winner_id = ? OR loser_id = ?
                """,
                Integer.class,
                participant.getId(),
                participant.getId(),
                participant.getId(),
                participant.getId()
        );
        assertNotNull(delta);
        assertEquals(1000 + delta, participant.getRating());
        assertEquals(gamesPlayed, participant.getGamesPlayed());
    }

    private void assertFinishedAndRanksMatchCurrentStandings(long seasonId) {
        SeasonEntity season = seasonRepository.findById(seasonId).orElseThrow();
        assertEquals(SeasonStatus.FINISHED, season.getStatus());
        assertNotNull(season.getEndDate());
        List<Integer> ranksInCurrentOrder = jdbcTemplate.queryForList(
                """
                SELECT final_rank
                FROM season_players
                WHERE season_id = ?
                ORDER BY rating DESC, games_played DESC, player_id ASC
                """,
                Integer.class,
                seasonId
        );
        assertEquals(
                java.util.stream.IntStream.rangeClosed(1, ranksInCurrentOrder.size()).boxed().toList(),
                ranksInCurrentOrder
        );
    }

    private <A, B> RaceResult<A, B> race(Callable<A> first, Callable<B> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Outcome<A>> firstFuture = executor.submit(guarded(first, ready, start));
            Future<Outcome<B>> secondFuture = executor.submit(guarded(second, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "workers did not reach the start barrier");
            start.countDown();
            return new RaceResult<>(
                    firstFuture.get(20, TimeUnit.SECONDS),
                    secondFuture.get(20, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private <T> Callable<Outcome<T>> guarded(
            Callable<T> action,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                return new Outcome<>(action.call(), null);
            } catch (Throwable failure) {
                return new Outcome<>(null, failure);
            }
        };
    }

    private record Outcome<T>(T value, Throwable failure) {
    }

    private record RaceResult<A, B>(Outcome<A> first, Outcome<B> second) {
    }
}
