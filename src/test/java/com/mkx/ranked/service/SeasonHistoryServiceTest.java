package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.SeasonNotFoundException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.dto.AdminSeasonStatisticsDto;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.SeasonPlayerHistoryDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import com.mkx.ranked.repository.SeasonRepository;
import com.mkx.ranked.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonHistoryServiceTest {

    private SeasonRepository seasonRepository;
    private SeasonPlayerRepository seasonPlayerRepository;
    private SeasonHistoryService service;
    private LeaderboardService leaderboardService;
    private MatchRepository matchRepository;
    private SeasonEntity finishedSeason;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(SeasonRepository.class);
        seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = new SeasonService(seasonRepository, seasonPlayerRepository);
        leaderboardService = mock(LeaderboardService.class);
        matchRepository = mock(MatchRepository.class);
        service = new SeasonHistoryService(
                seasonRepository,
                seasonPlayerRepository,
                seasonService,
                leaderboardService,
                matchRepository
        );

        finishedSeason = new SeasonEntity(3, "Final Season", LocalDateTime.of(2026, 8, 30, 18, 0));
        ReflectionTestUtils.setField(finishedSeason, "id", 30L);
        finishedSeason.setStatus(SeasonStatus.FINISHED);
        finishedSeason.setStartDate(LocalDateTime.of(2026, 8, 1, 18, 0));
        finishedSeason.setEndDate(LocalDateTime.of(2026, 8, 31, 22, 0));
    }

    @Test
    void returnsFinishedSeasonsWithLifecycleDates() {
        when(seasonRepository.findAllByStatusOrderByEndDateDescSeasonNumberDesc(SeasonStatus.FINISHED))
                .thenReturn(List.of(finishedSeason));

        List<SeasonDto> result = service.getFinishedSeasons();

        assertEquals(1, result.size());
        assertEquals(finishedSeason.getStartDate(), result.get(0).startDate());
        assertEquals(finishedSeason.getEndDate(), result.get(0).endDate());
        verify(seasonRepository).findAllByStatusOrderByEndDateDescSeasonNumberDesc(SeasonStatus.FINISHED);
    }

    @Test
    void findsFinishedSeasonByIdAndSeasonNumber() {
        when(seasonRepository.findById(30L)).thenReturn(Optional.of(finishedSeason));
        when(seasonRepository.findBySeasonNumber(3)).thenReturn(Optional.of(finishedSeason));

        assertEquals(30L, service.getFinishedSeason(30L).id());
        assertEquals(3, service.getFinishedSeasonByNumber(3).seasonNumber());
    }

    @Test
    void missingOrNonFinishedSeasonCannotBeReadAsHistory() {
        SeasonEntity active = new SeasonEntity(4, "Active", null);
        ReflectionTestUtils.setField(active, "id", 40L);
        active.setStatus(SeasonStatus.ACTIVE);
        when(seasonRepository.findById(99L)).thenReturn(Optional.empty());
        when(seasonRepository.findById(40L)).thenReturn(Optional.of(active));

        assertThrows(SeasonNotFoundException.class, () -> service.getFinishedSeason(99L));
        assertThrows(BusinessException.class, () -> service.getFinishedSeason(40L));
    }

    @Test
    void finalLeaderboardIsReturnedOnlyAfterFinishedValidation() {
        LeaderboardEntryDto entry = leaderboardEntry(1, 1400);
        when(seasonRepository.findById(30L)).thenReturn(Optional.of(finishedSeason));
        when(leaderboardService.getLeaderboardForSeason(30L)).thenReturn(List.of(entry));

        List<LeaderboardEntryDto> result = service.getFinishedSeasonLeaderboard(30L);

        assertEquals(List.of(entry), result);
    }

    @Test
    void returnsPlayerSnapshotFromFinishedSeason() {
        PlayerEntity player = new PlayerEntity(777L, "discord-user");
        ReflectionTestUtils.setField(player, "id", 7L);
        SeasonPlayerEntity seasonPlayer = new SeasonPlayerEntity(player, finishedSeason, "Sub-Zero");
        seasonPlayer.setRating(1350);
        seasonPlayer.setGamesPlayed(42);
        seasonPlayer.setFinalRank(4);
        when(seasonRepository.findById(30L)).thenReturn(Optional.of(finishedSeason));
        when(seasonPlayerRepository.findBySeasonAndPlayerDiscordId(finishedSeason, 777L))
                .thenReturn(Optional.of(seasonPlayer));

        Optional<SeasonPlayerHistoryDto> result = service.findPlayerInFinishedSeason(30L, 777L);

        assertTrue(result.isPresent());
        assertEquals("Sub-Zero", result.orElseThrow().displayName());
        assertEquals(4, result.orElseThrow().finalRank());
        assertEquals(1350, result.orElseThrow().rating());
    }

    @Test
    void absentPlayerProducesEmptySeasonSnapshot() {
        when(seasonRepository.findById(30L)).thenReturn(Optional.of(finishedSeason));
        when(seasonPlayerRepository.findBySeasonAndPlayerDiscordId(finishedSeason, 999L))
                .thenReturn(Optional.empty());

        Optional<SeasonPlayerHistoryDto> result = service.findPlayerInFinishedSeason(30L, 999L);

        assertFalse(result.isPresent());
    }

    @Test
    void returnsFinishedSeasonStatisticsWithTopAndAverageRating() {
        List<LeaderboardEntryDto> leaderboard = List.of(
                leaderboardEntry(1, 1400),
                leaderboardEntry(2, 1200)
        );
        when(seasonRepository.findById(30L)).thenReturn(Optional.of(finishedSeason));
        when(seasonPlayerRepository.countBySeason(finishedSeason)).thenReturn(2L);
        when(matchRepository.countBySeason(finishedSeason)).thenReturn(15L);
        when(leaderboardService.getLeaderboardForSeason(30L)).thenReturn(leaderboard);

        AdminSeasonStatisticsDto result = service.getFinishedSeasonStatistics(30L);

        assertEquals(30L, result.season().id());
        assertEquals(2L, result.playerCount());
        assertEquals(15L, result.matchCount());
        assertEquals(1300, result.averageRating());
        assertEquals(List.of(1, 2), result.topPlayers().stream().map(LeaderboardEntryDto::rank).toList());
    }

    private LeaderboardEntryDto leaderboardEntry(int rank, int rating) {
        return new LeaderboardEntryDto(
                rank,
                rank,
                100L + rank,
                "discord-player-" + rank,
                "Player " + rank,
                rating,
                10,
                "Tier",
                "🏆"
        );
    }
}
