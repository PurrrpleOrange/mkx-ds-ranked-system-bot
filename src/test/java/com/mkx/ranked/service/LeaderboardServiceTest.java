package com.mkx.ranked.service;

import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardServiceTest {

    @Test
    void activeLeaderboardPreservesRepositoryTieBreakOrderAndAssignsRanks() {
        SeasonPlayerRepository repository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        LeaderboardService service = new LeaderboardService(repository, seasonService);
        SeasonEntity season = season(1L, SeasonStatus.ACTIVE);
        SeasonPlayerEntity higherGames = seasonPlayer(12L, 102L, season, null);
        SeasonPlayerEntity lowerPlayerId = seasonPlayer(11L, 101L, season, null);
        higherGames.setRating(1200);
        higherGames.setGamesPlayed(10);
        lowerPlayerId.setRating(1200);
        lowerPlayerId.setGamesPlayed(8);
        when(seasonService.getActiveSeasonEntity()).thenReturn(season);
        when(repository.findLeaderboardBySeason(season)).thenReturn(List.of(higherGames, lowerPlayerId));

        List<LeaderboardEntryDto> result = service.getFullLeaderboardForActiveSeason();

        assertEquals(List.of(102L, 101L), result.stream().map(LeaderboardEntryDto::playerId).toList());
        assertEquals(List.of(1, 2), result.stream().map(LeaderboardEntryDto::rank).toList());
    }

    @Test
    void emptyLeaderboardReturnsEmptyList() {
        SeasonPlayerRepository repository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        LeaderboardService service = new LeaderboardService(repository, seasonService);
        SeasonEntity season = season(1L, SeasonStatus.ACTIVE);
        when(seasonService.getActiveSeasonEntity()).thenReturn(season);
        when(repository.findLeaderboardBySeason(season)).thenReturn(List.of());

        assertEquals(List.of(), service.getFullLeaderboardForActiveSeason());
    }

    @Test
    void paginatedLeaderboardUsesPageOffsetAndRequestedSize() {
        SeasonPlayerRepository repository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        LeaderboardService service = new LeaderboardService(repository, seasonService);
        SeasonEntity season = season(1L, SeasonStatus.ACTIVE);
        SeasonPlayerEntity third = seasonPlayer(13L, 103L, season, null);
        SeasonPlayerEntity fourth = seasonPlayer(14L, 104L, season, null);
        PageRequest request = PageRequest.of(1, 2);
        when(seasonService.getActiveSeasonEntity()).thenReturn(season);
        when(repository.findLeaderboardBySeason(season, request))
                .thenReturn(new PageImpl<>(List.of(third, fourth), request, 5));

        PageDto<LeaderboardEntryDto> result = service.getLeaderboardForActiveSeason(1, 2);

        assertEquals(List.of(3, 4), result.content().stream().map(LeaderboardEntryDto::rank).toList());
        assertEquals(1, result.currentPage());
        assertEquals(3, result.totalPages());
        assertEquals(5, result.totalItems());
        assertEquals(2, result.pageSize());
    }

    @Test
    void finishedLeaderboardUsesPersistedFinalRank() {
        SeasonPlayerRepository seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        LeaderboardService service = new LeaderboardService(seasonPlayerRepository, seasonService);
        SeasonEntity season = season(1L, SeasonStatus.FINISHED);
        SeasonPlayerEntity second = seasonPlayer(12L, 102L, season, 2);
        SeasonPlayerEntity first = seasonPlayer(11L, 101L, season, 1);

        when(seasonService.getSeasonEntityById(1L)).thenReturn(season);
        when(seasonPlayerRepository.findFinalLeaderboardBySeason(season))
                .thenReturn(List.of(second, first));

        List<LeaderboardEntryDto> result = service.getLeaderboardForSeason(1L);

        assertEquals(List.of(2, 1), result.stream().map(LeaderboardEntryDto::rank).toList());
        verify(seasonPlayerRepository, never()).findLeaderboardBySeason(season);
    }

    private SeasonPlayerEntity seasonPlayer(long id, long playerId, SeasonEntity season, Integer finalRank) {
        PlayerEntity player = new PlayerEntity(playerId + 1000, "player-" + playerId);
        ReflectionTestUtils.setField(player, "id", playerId);
        SeasonPlayerEntity seasonPlayer = new SeasonPlayerEntity(player, season, "Player " + playerId);
        ReflectionTestUtils.setField(seasonPlayer, "id", id);
        seasonPlayer.setFinalRank(finalRank);
        return seasonPlayer;
    }

    private SeasonEntity season(long id, SeasonStatus status) {
        SeasonEntity season = new SeasonEntity(Math.toIntExact(id), "Season " + id, null);
        ReflectionTestUtils.setField(season, "id", id);
        season.setStatus(status);
        return season;
    }
}
