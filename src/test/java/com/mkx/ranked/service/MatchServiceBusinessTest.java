package com.mkx.ranked.service;

import com.mkx.ranked.exception.InvalidMatchException;
import com.mkx.ranked.exception.MatchNotFoundException;
import com.mkx.ranked.exception.PlayerNotRegisteredException;
import com.mkx.ranked.exception.SeasonNotActiveException;
import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchServiceBusinessTest {

    private PlayerRepository playerRepository;
    private SeasonPlayerRepository seasonPlayerRepository;
    private MatchRepository matchRepository;
    private SeasonService seasonService;
    private MatchService service;
    private SeasonEntity activeSeason;
    private PlayerEntity winnerPlayer;
    private PlayerEntity loserPlayer;
    private SeasonPlayerEntity winner;
    private SeasonPlayerEntity loser;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        matchRepository = mock(MatchRepository.class);
        seasonService = mock(SeasonService.class);
        service = new MatchService(playerRepository, seasonPlayerRepository, matchRepository, seasonService);

        activeSeason = season(10L, SeasonStatus.ACTIVE);
        winnerPlayer = player(1L, 11L, "winner-discord");
        loserPlayer = player(2L, 22L, "loser-discord");
        winner = participant(101L, winnerPlayer, activeSeason, "Winner", 1000, 0);
        loser = participant(102L, loserPlayer, activeSeason, "Loser", 1000, 0);

        when(playerRepository.findByDiscordId(11L)).thenReturn(Optional.of(winnerPlayer));
        when(playerRepository.findByDiscordId(22L)).thenReturn(Optional.of(loserPlayer));
        when(seasonService.getActiveSeasonEntityForReadLock()).thenReturn(activeSeason);
        when(seasonPlayerRepository.findAllBySeasonAndPlayerInForUpdate(activeSeason, List.of(winnerPlayer, loserPlayer)))
                .thenReturn(List.of(winner, loser));
        when(matchRepository.save(any(MatchEntity.class))).thenAnswer(invocation -> {
            MatchEntity match = invocation.getArgument(0);
            ReflectionTestUtils.setField(match, "id", 501L);
            return match;
        });
    }

    @Test
    void validMatchUpdatesBothParticipantsAndPersistsAppliedDeltas() {
        MatchResult result = service.processMatchResult(11L, 22L, 5, 2);

        ArgumentCaptor<MatchEntity> captor = ArgumentCaptor.forClass(MatchEntity.class);
        verify(matchRepository).save(captor.capture());
        MatchEntity persisted = captor.getValue();

        assertEquals(1, winner.getGamesPlayed());
        assertEquals(1, loser.getGamesPlayed());
        assertEquals(1000 + persisted.getDeltaWinner(), winner.getRating());
        assertEquals(1000 + persisted.getDeltaLoser(), loser.getRating());
        assertEquals(winner.getRating(), result.newWinnerRating());
        assertEquals(loser.getRating(), result.newLoserRating());
        assertEquals(5, persisted.getWinnerScore());
        assertEquals(2, persisted.getLoserScore());
    }

    @Test
    void confirmationCorrectlyDeterminesOpponentAsWinner() {
        when(seasonPlayerRepository.findAllBySeasonAndPlayerInForUpdate(activeSeason, List.of(loserPlayer, winnerPlayer)))
                .thenReturn(List.of(winner, loser));

        MatchResult result = service.confirmReportedMatch(11L, 22L, 3, 5);

        assertEquals(22L, result.winnerDiscordId());
        assertEquals(11L, result.loserDiscordId());
        assertEquals(5, result.winnerScore());
        assertEquals(3, result.loserScore());
    }

    @Test
    void rejectsSelfMatchAndInvalidFt5ScoresBeforeDatabaseAccess() {
        assertThrows(InvalidMatchException.class, () -> service.processMatchResult(11L, 11L, 5, 0));
        assertThrows(InvalidMatchException.class, () -> service.processMatchResult(11L, 22L, 4, 3));
        assertThrows(InvalidMatchException.class, () -> service.confirmReportedMatch(11L, 22L, 5, 5));

        verify(matchRepository, never()).save(any());
    }

    @Test
    void rejectsMatchWithoutActiveSeason() {
        when(seasonService.getActiveSeasonEntityForReadLock()).thenThrow(new SeasonNotActiveException());

        assertThrows(SeasonNotActiveException.class, () -> service.processMatchResult(11L, 22L, 5, 0));

        verify(seasonPlayerRepository, never()).save(any());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void rejectsPlayerWhoIsNotRegisteredInActiveSeason() {
        when(seasonPlayerRepository.findAllBySeasonAndPlayerInForUpdate(activeSeason, List.of(winnerPlayer, loserPlayer)))
                .thenReturn(List.of(winner));

        assertThrows(PlayerNotRegisteredException.class, () -> service.processMatchResult(11L, 22L, 5, 0));

        verify(matchRepository, never()).save(any());
    }

    @Test
    void rejectsParticipantsReturnedFromDifferentSeason() {
        SeasonEntity otherSeason = season(20L, SeasonStatus.ACTIVE);
        SeasonPlayerEntity foreignLoser = participant(202L, loserPlayer, otherSeason, "Loser", 1000, 0);
        when(seasonPlayerRepository.findAllBySeasonAndPlayerInForUpdate(activeSeason, List.of(winnerPlayer, loserPlayer)))
                .thenReturn(List.of(winner, foreignLoser));

        assertThrows(InvalidMatchException.class, () -> service.processMatchResult(11L, 22L, 5, 0));

        verify(matchRepository, never()).save(any());
    }

    @Test
    void rollbackRestoresRatingsAndGamesThenDeletesMatch() {
        winner.setRating(1012);
        winner.setGamesPlayed(1);
        loser.setRating(988);
        loser.setGamesPlayed(1);
        MatchEntity match = match(501L, activeSeason, winner, loser, 12, -12);
        when(matchRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(match));
        when(seasonPlayerRepository.findAllByIdInForUpdate(List.of(101L, 102L)))
                .thenReturn(List.of(winner, loser));

        service.revertMatch(501L);

        assertEquals(1000, winner.getRating());
        assertEquals(1000, loser.getRating());
        assertEquals(0, winner.getGamesPlayed());
        assertEquals(0, loser.getGamesPlayed());
        verify(matchRepository).delete(match);
    }

    @Test
    void repeatedRollbackIsImpossibleAfterMatchWasDeleted() {
        when(matchRepository.findByIdForUpdate(501L)).thenReturn(Optional.empty());

        assertThrows(MatchNotFoundException.class, () -> service.revertMatch(501L));

        verify(seasonPlayerRepository, never()).findAllByIdInForUpdate(any());
    }

    @Test
    void adminMatchLookupRejectsUnknownMatch() {
        when(matchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(MatchNotFoundException.class, () -> service.getAdminMatchInfo(999L));
    }

    @Test
    void rollbackCannotMakeGamesOrRatingNegative() {
        winner.setGamesPlayed(0);
        loser.setGamesPlayed(1);
        MatchEntity noGames = match(501L, activeSeason, winner, loser, 10, -10);
        when(matchRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(noGames));
        when(seasonPlayerRepository.findAllByIdInForUpdate(List.of(101L, 102L)))
                .thenReturn(List.of(winner, loser));
        assertThrows(InvalidMatchException.class, () -> service.revertMatch(501L));

        winner.setGamesPlayed(1);
        winner.setRating(2);
        MatchEntity negativeRating = match(502L, activeSeason, winner, loser, 10, -10);
        when(matchRepository.findByIdForUpdate(502L)).thenReturn(Optional.of(negativeRating));
        assertThrows(InvalidMatchException.class, () -> service.revertMatch(502L));

        verify(matchRepository, never()).delete(any());
    }

    private SeasonEntity season(long id, SeasonStatus status) {
        SeasonEntity season = new SeasonEntity(Math.toIntExact(id), "Season", null);
        ReflectionTestUtils.setField(season, "id", id);
        season.setStatus(status);
        return season;
    }

    private PlayerEntity player(long id, long discordId, String username) {
        PlayerEntity player = new PlayerEntity(discordId, username);
        ReflectionTestUtils.setField(player, "id", id);
        return player;
    }

    private SeasonPlayerEntity participant(
            long id, PlayerEntity player, SeasonEntity season, String name, int rating, int games
    ) {
        SeasonPlayerEntity participant = new SeasonPlayerEntity(player, season, name);
        ReflectionTestUtils.setField(participant, "id", id);
        participant.setRating(rating);
        participant.setGamesPlayed(games);
        return participant;
    }

    private MatchEntity match(
            long id, SeasonEntity season, SeasonPlayerEntity matchWinner, SeasonPlayerEntity matchLoser,
            int winnerDelta, int loserDelta
    ) {
        MatchEntity match = new MatchEntity(season, matchWinner, matchLoser, 5, 2, winnerDelta, loserDelta);
        ReflectionTestUtils.setField(match, "id", id);
        return match;
    }
}
