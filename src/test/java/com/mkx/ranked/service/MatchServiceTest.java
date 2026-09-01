package com.mkx.ranked.service;

import com.mkx.ranked.exception.InvalidMatchException;
import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(winner.getDisplayName()).thenReturn("Season Winner");
        when(loser.getDisplayName()).thenReturn("Season Loser");

        when(playerRepository.findByDiscordId(11L)).thenReturn(Optional.of(winnerPlayer));
        when(playerRepository.findByDiscordId(22L)).thenReturn(Optional.of(loserPlayer));
        when(seasonService.getActiveSeasonEntityForReadLock()).thenReturn(season);
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
        assertEquals("Season Winner", result.winnerDisplayName());
        assertEquals("Season Loser", result.loserDisplayName());
        assertEquals(-5, persisted.getDeltaLoser());
        assertEquals(5 + persisted.getDeltaLoser(), result.newLoserRating());
        assertEquals(result.newLoserRating() - persisted.getDeltaLoser(), 5);
    }

    @Test
    void cannotRevertMatchFromFinishedSeason() {
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

        SeasonEntity season = mock(SeasonEntity.class);
        when(season.getStatus()).thenReturn(SeasonStatus.FINISHED);
        MatchEntity match = mock(MatchEntity.class);
        when(match.getSeason()).thenReturn(season);
        when(matchRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(match));

        InvalidMatchException exception = assertThrows(
                InvalidMatchException.class,
                () -> service.revertMatch(501L)
        );

        assertEquals("Cannot revert a match from a FINISHED season.", exception.getMessage());
        verify(seasonPlayerRepository, never()).findAllByIdInForUpdate(any());
        verify(matchRepository, never()).delete(any());
    }

    @Test
    void adminMatchInfoMapsLazyGraphInsideMatchService() {
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

        PlayerEntity winnerPlayer = player(1L, 11L, "Winner Discord");
        PlayerEntity loserPlayer = player(2L, 22L, "Loser Discord");
        SeasonEntity season = mock(SeasonEntity.class);
        SeasonPlayerEntity winner = mock(SeasonPlayerEntity.class);
        SeasonPlayerEntity loser = mock(SeasonPlayerEntity.class);
        MatchEntity match = mock(MatchEntity.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 31, 20, 30);

        when(season.getSeasonNumber()).thenReturn(9);
        when(winner.getDisplayName()).thenReturn("Sub-Zero");
        when(winner.getPlayer()).thenReturn(winnerPlayer);
        when(loser.getDisplayName()).thenReturn("Scorpion");
        when(loser.getPlayer()).thenReturn(loserPlayer);
        when(match.getId()).thenReturn(501L);
        when(match.getSeason()).thenReturn(season);
        when(match.getWinner()).thenReturn(winner);
        when(match.getLoser()).thenReturn(loser);
        when(match.getWinnerScore()).thenReturn(5);
        when(match.getLoserScore()).thenReturn(3);
        when(match.getDeltaWinner()).thenReturn(21);
        when(match.getDeltaLoser()).thenReturn(-21);
        when(match.getCreatedAt()).thenReturn(createdAt);
        when(matchRepository.findById(501L)).thenReturn(Optional.of(match));

        AdminMatchDto result = service.getAdminMatchInfo(501L);

        assertEquals(501L, result.matchId());
        assertEquals(9, result.seasonNumber());
        assertEquals("Sub-Zero", result.winnerDisplayName());
        assertEquals("Scorpion", result.loserDisplayName());
        assertEquals(11L, result.winnerDiscordId());
        assertEquals(22L, result.loserDiscordId());
        assertEquals(5, result.winnerScore());
        assertEquals(3, result.loserScore());
        assertEquals(21, result.deltaWinner());
        assertEquals(-21, result.deltaLoser());
        assertEquals(createdAt, result.createdAt());
    }

    private PlayerEntity player(long id, long discordId, String displayName) {
        PlayerEntity player = mock(PlayerEntity.class);
        when(player.getId()).thenReturn(id);
        when(player.getDiscordId()).thenReturn(discordId);
        when(player.getUsername()).thenReturn(displayName);
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
        String displayName = player.getUsername();
        when(seasonPlayer.getId()).thenReturn(id);
        when(seasonPlayer.getPlayer()).thenReturn(player);
        when(seasonPlayer.getSeason()).thenReturn(season);
        when(seasonPlayer.getDisplayName()).thenReturn(displayName);
        when(seasonPlayer.getRating()).thenAnswer(ignored -> rating.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            rating.set(invocation.getArgument(0));
            return null;
        }).when(seasonPlayer).setRating(any(Integer.class));
        when(seasonPlayer.getGamesPlayed()).thenReturn(gamesPlayed);
        return seasonPlayer;
    }
}
