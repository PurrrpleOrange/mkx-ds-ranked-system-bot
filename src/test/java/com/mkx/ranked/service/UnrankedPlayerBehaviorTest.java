package com.mkx.ranked.service;

import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.SeasonPlayerHistoryDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import com.mkx.ranked.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnrankedPlayerBehaviorTest {

    @Test
    void profileBeforeFirstMatchIsUnrankedWithoutLoadingLeaderboard() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        SeasonPlayerRepository seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        PlayerService service = new PlayerService(playerRepository, seasonPlayerRepository, seasonService);
        PlayerEntity player = player(10L, 123L);
        SeasonEntity season = season(20L, SeasonStatus.ACTIVE);
        SeasonPlayerEntity participation = participation(player, season);

        when(playerRepository.findByDiscordId(123L)).thenReturn(Optional.of(player));
        when(seasonService.getActiveSeasonEntity()).thenReturn(season);
        when(seasonPlayerRepository.findBySeasonAndPlayer(season, player)).thenReturn(Optional.of(participation));

        PlayerProfileDto profile = service.getProfile(123L);

        assertNull(profile.rank());
        assertEquals("Без ранга", profile.tierName());
        assertEquals(0, profile.gamesPlayed());
        verify(seasonPlayerRepository, never()).findLeaderboardBySeason(season);
    }

    @Test
    void finishedSeasonKeepsRegisteredPlayerWithoutFinalRank() {
        SeasonRepository seasonRepository = mock(SeasonRepository.class);
        SeasonPlayerRepository seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        LeaderboardService leaderboardService = mock(LeaderboardService.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        SeasonHistoryService service = new SeasonHistoryService(
                seasonRepository,
                seasonPlayerRepository,
                seasonService,
                leaderboardService,
                matchRepository
        );
        PlayerEntity player = player(10L, 123L);
        SeasonEntity season = season(20L, SeasonStatus.FINISHED);
        SeasonPlayerEntity participation = participation(player, season);

        when(seasonRepository.findById(20L)).thenReturn(Optional.of(season));
        when(seasonPlayerRepository.findBySeasonAndPlayerDiscordId(season, 123L))
                .thenReturn(Optional.of(participation));

        SeasonPlayerHistoryDto history = service.findPlayerInFinishedSeason(20L, 123L).orElseThrow();

        assertNull(history.finalRank());
        assertEquals("Без ранга", history.tierName());
        assertEquals(0, history.gamesPlayed());
    }

    private PlayerEntity player(long id, long discordId) {
        PlayerEntity player = new PlayerEntity(discordId, "player-" + discordId);
        ReflectionTestUtils.setField(player, "id", id);
        return player;
    }

    private SeasonEntity season(long id, SeasonStatus status) {
        SeasonEntity season = new SeasonEntity(Math.toIntExact(id), "Season " + id, null);
        ReflectionTestUtils.setField(season, "id", id);
        season.setStatus(status);
        return season;
    }

    private SeasonPlayerEntity participation(PlayerEntity player, SeasonEntity season) {
        SeasonPlayerEntity participation = new SeasonPlayerEntity(player, season, "Player");
        ReflectionTestUtils.setField(participation, "id", 30L);
        return participation;
    }
}
