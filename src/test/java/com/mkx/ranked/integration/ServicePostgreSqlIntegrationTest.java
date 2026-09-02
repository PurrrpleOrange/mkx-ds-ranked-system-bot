package com.mkx.ranked.integration;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.InvalidMatchException;
import com.mkx.ranked.exception.MatchNotFoundException;
import com.mkx.ranked.exception.SeasonNotActiveException;
import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.dto.SeasonPlayerHistoryDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import com.mkx.ranked.repository.SeasonRepository;
import com.mkx.ranked.service.LeaderboardService;
import com.mkx.ranked.service.MatchService;
import com.mkx.ranked.service.RegistrationService;
import com.mkx.ranked.service.SeasonHistoryService;
import com.mkx.ranked.service.SeasonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicePostgreSqlIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    SeasonService seasonService;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    MatchService matchService;

    @Autowired
    LeaderboardService leaderboardService;

    @Autowired
    SeasonHistoryService seasonHistoryService;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    SeasonPlayerRepository seasonPlayerRepository;

    @Autowired
    MatchRepository matchRepository;

    @Test
    void registrationLifecycleUsesSeasonScopedIdentityAndPreservesFinishedSnapshot() {
        SeasonDto firstSeason = createAndActivateSeason("First");

        RegistrationResultDto first = registrationService.register(11L, "discord-one", "Scorpion");
        SeasonPlayerEntity firstParticipation = participation(firstSeason.id(), 11L);
        assertEquals(1000, firstParticipation.getRating());
        assertEquals(0, firstParticipation.getGamesPlayed());
        assertNull(firstParticipation.getFinalRank());
        assertEquals(1, playerRepository.count());

        assertThrows(
                BusinessException.class,
                () -> registrationService.register(11L, "discord-one", "AnotherName")
        );
        assertThrows(
                BusinessException.class,
                () -> registrationService.register(22L, "discord-two", "scorpion")
        );

        seasonService.finishActiveSeason();
        SeasonPlayerHistoryDto historical = seasonHistoryService
                .findPlayerInFinishedSeason(firstSeason.id(), 11L)
                .orElseThrow();
        assertEquals(1, historical.finalRank());
        assertThrows(
                SeasonNotActiveException.class,
                () -> registrationService.register(22L, "discord-two", "Sub-Zero")
        );

        SeasonDto secondSeason = createAndActivateSeason("Second");
        RegistrationResultDto second = registrationService.register(11L, "renamed-discord", "scorpion");

        assertEquals(secondSeason.id(), second.seasonId());
        assertEquals(first.playerId(), second.playerId());
        assertEquals(1, playerRepository.count());
        SeasonPlayerHistoryDto unchanged = seasonHistoryService
                .findPlayerInFinishedSeason(firstSeason.id(), 11L)
                .orElseThrow();
        assertEquals("Scorpion", unchanged.displayName());
        assertEquals(1000, unchanged.rating());
        assertEquals(1, unchanged.finalRank());
    }

    @Test
    void leaderboardPaginationTieBreakAndFinalRanksUseDatabaseOrdering() {
        SeasonDto season = createAndActivateSeason("Ranked");
        register(11L, "First");
        register(22L, "Second");
        register(33L, "Third");
        setStanding(season.id(), 11L, 1200, 8);
        setStanding(season.id(), 22L, 1200, 10);
        setStanding(season.id(), 33L, 1200, 10);

        List<Long> expectedOrder = List.of(
                playerRepository.findByDiscordId(22L).orElseThrow().getId(),
                playerRepository.findByDiscordId(33L).orElseThrow().getId(),
                playerRepository.findByDiscordId(11L).orElseThrow().getId()
        );
        List<LeaderboardEntryDto> full = leaderboardService.getFullLeaderboardForActiveSeason();
        PageDto<LeaderboardEntryDto> firstPage = leaderboardService.getLeaderboardForActiveSeason(0, 2);
        PageDto<LeaderboardEntryDto> secondPage = leaderboardService.getLeaderboardForActiveSeason(1, 2);

        assertEquals(expectedOrder, full.stream().map(LeaderboardEntryDto::playerId).toList());
        assertEquals(List.of(1, 2), firstPage.content().stream().map(LeaderboardEntryDto::rank).toList());
        assertEquals(List.of(3), secondPage.content().stream().map(LeaderboardEntryDto::rank).toList());
        assertEquals(2, firstPage.pageSize());
        assertEquals(3, firstPage.totalItems());

        SeasonDto finished = seasonService.finishActiveSeason();
        assertEquals(SeasonStatus.FINISHED, finished.status());
        assertNotNull(finished.endDate());
        List<LeaderboardEntryDto> historical = leaderboardService.getLeaderboardForSeason(season.id());
        assertEquals(expectedOrder, historical.stream().map(LeaderboardEntryDto::playerId).toList());
        assertEquals(List.of(1, 2, 3), historical.stream().map(LeaderboardEntryDto::rank).toList());
    }

    @Test
    void emptyLeaderboardAndFinishedSeasonHistoryBehaveDeterministically() {
        SeasonDto empty = createAndActivateSeason("Empty");
        assertTrue(leaderboardService.getFullLeaderboardForActiveSeason().isEmpty());
        seasonService.finishActiveSeason();
        seasonService.createNewSeason("Created only", null);

        assertEquals(List.of(empty.id()), seasonHistoryService.getFinishedSeasons()
                .stream().map(SeasonDto::id).toList());
        assertEquals(empty.id(), seasonHistoryService.getFinishedSeason(empty.id()).id());
        assertEquals(empty.seasonNumber(), seasonHistoryService
                .getFinishedSeasonByNumber(empty.seasonNumber()).seasonNumber());
        assertTrue(seasonHistoryService.getFinishedSeasonLeaderboard(empty.id()).isEmpty());
        assertTrue(seasonHistoryService.findPlayerInFinishedSeason(empty.id(), 999L).isEmpty());
    }

    @Test
    void matchPersistsAppliedFloorDeltaAndRollbackExactlyRestoresState() {
        SeasonDto season = createAndActivateSeason("Matches");
        register(11L, "Winner");
        register(22L, "Loser");
        setStanding(season.id(), 11L, 0, 10);
        setStanding(season.id(), 22L, 5, 10);

        MatchResult result = matchService.processMatchResult(11L, 22L, 5, 0);
        MatchEntity persisted = matchRepository.findById(result.matchId()).orElseThrow();

        assertEquals(0, result.newLoserRating());
        assertEquals(-5, persisted.getDeltaLoser());
        assertEquals(5 + persisted.getDeltaLoser(), result.newLoserRating());
        assertEquals(0, result.newWinnerRating() - persisted.getDeltaWinner());
        assertEquals(11, participation(season.id(), 11L).getGamesPlayed());
        assertEquals(11, participation(season.id(), 22L).getGamesPlayed());

        matchService.revertMatch(result.matchId());

        assertEquals(0, participation(season.id(), 11L).getRating());
        assertEquals(5, participation(season.id(), 22L).getRating());
        assertEquals(10, participation(season.id(), 11L).getGamesPlayed());
        assertEquals(10, participation(season.id(), 22L).getGamesPlayed());
        assertThrows(MatchNotFoundException.class, () -> matchService.revertMatch(result.matchId()));
    }

    @Test
    void finishedSeasonRejectsNewMatchesAndRollback() {
        createAndActivateSeason("Finished matches");
        register(11L, "Winner");
        register(22L, "Loser");
        MatchResult result = matchService.processMatchResult(11L, 22L, 5, 1);
        seasonService.finishActiveSeason();

        assertThrows(SeasonNotActiveException.class, () -> matchService.processMatchResult(11L, 22L, 5, 1));
        assertThrows(InvalidMatchException.class, () -> matchService.revertMatch(result.matchId()));
        assertEquals(1, matchRepository.count());
    }

    @Test
    void lifecycleRejectsEveryInvalidTransitionAndSecondActiveSeason() {
        SeasonDto created = seasonService.createNewSeason("Created", null);
        assertThrows(BusinessException.class, () -> seasonService.finishSeason(created.id()));
        SeasonDto active = seasonService.activateSeason(created.id());
        assertNotNull(active.startDate());
        assertThrows(BusinessException.class, () -> seasonService.activateSeason(created.id()));

        SeasonDto second = seasonService.createNewSeason("Second", null);
        assertThrows(BusinessException.class, () -> seasonService.activateSeason(second.id()));

        seasonService.finishSeason(created.id());
        assertThrows(BusinessException.class, () -> seasonService.finishSeason(created.id()));
        assertThrows(BusinessException.class, () -> seasonService.activateSeason(created.id()));
    }

    private SeasonDto createAndActivateSeason(String name) {
        SeasonDto created = seasonService.createNewSeason(name, null);
        return seasonService.activateSeason(created.id());
    }

    private void register(long discordId, String displayName) {
        registrationService.register(discordId, "discord-" + discordId, displayName);
    }

    private SeasonPlayerEntity participation(long seasonId, long discordId) {
        SeasonEntity season = seasonRepository.findById(seasonId).orElseThrow();
        PlayerEntity player = playerRepository.findByDiscordId(discordId).orElseThrow();
        return seasonPlayerRepository.findBySeasonAndPlayer(season, player).orElseThrow();
    }

    private void setStanding(long seasonId, long discordId, int rating, int gamesPlayed) {
        SeasonPlayerEntity standing = participation(seasonId, discordId);
        standing.setRating(rating);
        standing.setGamesPlayed(gamesPlayed);
        seasonPlayerRepository.saveAndFlush(standing);
    }
}
