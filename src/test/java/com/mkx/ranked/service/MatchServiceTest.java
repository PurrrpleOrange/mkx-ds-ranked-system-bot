package com.mkx.ranked.service;

import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchServiceTest {

    @Test
    void persistsActuallyAppliedDeltaWhenLoserRatingReachesZero() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        SeasonPlayerRepository seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        MatchService service = new MatchService(
                playerRepository,
                seasonPlayerRepository,
                matchRepository,
                seasonService
        );

        PlayerEntity winnerPlayer = player(1L, 11L, "Winner");
        PlayerEntity loserPlayer = player(2L, 22L, "Loser");
        SeasonEntity season = mock(SeasonEntity.class);
        when(season.getId()).thenReturn(100L);

        AtomicInteger winnerRating = new AtomicInteger(0);
        AtomicInteger loserRating = new AtomicInteger(5);
        SeasonPlayerEntity winner = seasonPlayer(101L, winnerPlayer, season, winnerRating, 10);
        SeasonPlayerEntity loser = seasonPlayer(102L, loserPlayer, season, loserRating, 10);

        when(playerRepository.findByDiscordId(11L)).thenReturn(Optional.of(winnerPlayer));
        when(playerRepository.findByDiscordId(22L)).thenReturn(Optional.of(loserPlayer));
        when(seasonService.getCurrentSeasonEntity()).thenReturn(season);
        when(seasonPlayerRepository.findAllBySeasonAndPlayerInForUpdate(
                any(SeasonEntity.class), any()
        )).thenReturn(List.of(winner, loser));

        MatchEntity savedMatch = mock(MatchEntity.class);
        when(savedMatch.getId()).thenReturn(501L);
        when(matchRepository.save(any(MatchEntity.class))).thenReturn(savedMatch);

        int rawLoserDelta = EloCalculator.calculate(0, 10, 5, 10, 5, 0).deltaLoser();
        assertTrue(rawLoserDelta < -5, "test setup must exercise the zero-rating clamp");

        MatchResult result = service.processMatchResult(11L, 22L, 5, 0);

        ArgumentCaptor<MatchEntity> matchCaptor = ArgumentCaptor.forClass(MatchEntity.class);
        verify(matchRepository).save(matchCaptor.capture());
        MatchEntity persisted = matchCaptor.getValue();

        assertEquals(0, result.newLoserRating());
        assertEquals(-5, result.deltaLoser());
        assertEquals(-5, persisted.getDeltaLoser());
        assertEquals(5 + persisted.getDeltaLoser(), result.newLoserRating());
        assertEquals(result.newLoserRating() - persisted.getDeltaLoser(), 5);
    }

    private PlayerEntity player(long id, long discordId, String displayName) {
        PlayerEntity player = mock(PlayerEntity.class);
        when(player.getId()).thenReturn(id);
        when(player.getDiscordId()).thenReturn(discordId);
        when(player.getDisplayName()).thenReturn(displayName);
        return player;
    }

    private SeasonPlayerEntity seasonPlayer(
            long id,
            PlayerEntity player,
            SeasonEntity season,
            AtomicInteger rating,
            int gamesPlayed
    ) {
        SeasonPlayerEntity seasonPlayer = mock(SeasonPlayerEntity.class);
        when(seasonPlayer.getId()).thenReturn(id);
        when(seasonPlayer.getPlayer()).thenReturn(player);
        when(seasonPlayer.getSeason()).thenReturn(season);
        when(seasonPlayer.getRating()).thenAnswer(ignored -> rating.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            rating.set(invocation.getArgument(0));
            return null;
        }).when(seasonPlayer).setRating(any(Integer.class));
        when(seasonPlayer.getGamesPlayed()).thenReturn(gamesPlayed);
        return seasonPlayer;
    }
}
