package com.mkx.ranked.service;

import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardServiceTest {

    @Test
    void finishedLeaderboardUsesPersistedFinalRank() {
        SeasonPlayerRepository seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        LeaderboardService service = new LeaderboardService(seasonPlayerRepository, seasonService);
        SeasonEntity season = new SeasonEntity(1, "Finished", null);
        ReflectionTestUtils.setField(season, "id", 1L);
        season.setStatus(SeasonStatus.FINISHED);
        SeasonPlayerEntity second = seasonPlayer(12L, 102L, season, 2);
        SeasonPlayerEntity first = seasonPlayer(11L, 101L, season, 1);

        when(seasonService.getSeasonEntityById(1L)).thenReturn(season);
        when(seasonPlayerRepository.findFinalLeaderboardBySeason(season))
                .thenReturn(List.of(second, first));

        List<LeaderboardEntryDto> result = service.getLeaderboardForSeason(1L);

        assertEquals(List.of(2, 1), result.stream().map(LeaderboardEntryDto::rank).toList());
        verify(seasonPlayerRepository, never()).findLeaderboardBySeason(season);
    }

    private SeasonPlayerEntity seasonPlayer(long id, long playerId, SeasonEntity season, int finalRank) {
        PlayerEntity player = new PlayerEntity(playerId + 1000, "player-" + playerId);
        ReflectionTestUtils.setField(player, "id", playerId);
        SeasonPlayerEntity seasonPlayer = new SeasonPlayerEntity(player, season, "Player " + playerId);
        ReflectionTestUtils.setField(seasonPlayer, "id", id);
        seasonPlayer.setFinalRank(finalRank);
        return seasonPlayer;
    }
}
