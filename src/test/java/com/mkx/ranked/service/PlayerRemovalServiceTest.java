package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.PlayerNotRegisteredException;
import com.mkx.ranked.exception.PlayerRemovedFromSeasonException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerRemovalServiceTest {
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final SeasonPlayerRepository participants = mock(SeasonPlayerRepository.class);
    private final MatchRepository matches = mock(MatchRepository.class);
    private final SeasonService seasons = mock(SeasonService.class);
    private final PlayerService playerService = new PlayerService(players, participants, seasons);
    private final MatchService matchService = new MatchService(players, participants, matches, seasons);
    private final RegistrationService registrationService = new RegistrationService(players, participants, seasons);
    private final SeasonEntity season = new SeasonEntity(1, "Season", null);
    private final PlayerEntity player = new PlayerEntity(11L, "player");
    private final PlayerEntity opponent = new PlayerEntity(22L, "opponent");
    private final SeasonPlayerEntity participant = new SeasonPlayerEntity(player, season, "Scorpion");
    private final SeasonPlayerEntity other = new SeasonPlayerEntity(opponent, season, "Sub-Zero");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(season, "id", 1L);
        ReflectionTestUtils.setField(player, "id", 11L);
        ReflectionTestUtils.setField(opponent, "id", 22L);
        ReflectionTestUtils.setField(participant, "id", 111L);
        ReflectionTestUtils.setField(other, "id", 222L);
        participant.setRating(1150);
        participant.setGamesPlayed(7);
        other.setRating(850);
        other.setGamesPlayed(7);
        when(seasons.getActiveSeasonEntity()).thenReturn(season);
        when(seasons.getCurrentSeasonEntity()).thenReturn(season);
        when(seasons.getActiveSeasonEntityForReadLock()).thenReturn(season);
        when(players.findByDiscordId(11L)).thenReturn(Optional.of(player));
        when(players.findByDiscordId(22L)).thenReturn(Optional.of(opponent));
        when(participants.findBySeasonAndPlayer(season, player)).thenReturn(Optional.of(participant));
        when(participants.findBySeasonAndPlayer(season, opponent)).thenReturn(Optional.of(other));
        when(participants.findAllBySeasonAndPlayerInForUpdate(season, List.of(player)))
                .thenReturn(List.of(participant));
        when(participants.findAllBySeasonAndPlayerInForUpdate(season, List.of(player, opponent)))
                .thenReturn(List.of(participant, other));
        when(participants.findAllBySeasonAndPlayerInForUpdate(season, List.of(opponent, player)))
                .thenReturn(List.of(participant, other));
    }

    @Test
    void removalPreservesProgressAndRecordsAdministratorOnlyOnce() {
        playerService.removeFromSeason(1L, 11L, 999L);

        assertTrue(participant.isRemoved());
        assertEquals(999L, participant.getRemovedBy());
        var removedAt = participant.getRemovedAt();
        assertNotNull(removedAt);
        assertEquals(1150, participant.getRating());
        assertEquals(7, participant.getGamesPlayed());
        assertEquals(850, other.getRating());
        assertEquals(7, other.getGamesPlayed());
        assertNull(participant.getFinalRank());
        assertThrows(PlayerRemovedFromSeasonException.class,
                () -> playerService.removeFromSeason(1L, 11L, 888L));
        assertEquals(999L, participant.getRemovedBy());
        assertEquals(removedAt, participant.getRemovedAt());
        verify(participants, times(1)).save(participant);
        verifyNoInteractions(matches);
    }

    @Test
    void staleSeasonAndMissingParticipationCannotRemovePlayer() {
        assertThrows(BusinessException.class, () -> playerService.removeFromSeason(2L, 11L, 999L));
        verify(participants, never()).findAllBySeasonAndPlayerInForUpdate(any(), any());
        when(participants.findAllBySeasonAndPlayerInForUpdate(season, List.of(player))).thenReturn(List.of());
        assertThrows(PlayerNotRegisteredException.class, () -> playerService.removeFromSeason(1L, 11L, 999L));
        verify(participants, never()).save(any());
        assertFalse(participant.isRemoved());
    }

    @Test
    void removedPlayerCanReadProfileAndHistoryWithoutLeaderboardPosition() {
        participant.removeFromSeason(999L);
        when(participants.existsBySeasonAndPlayer(season, player)).thenReturn(true);
        when(matches.findAllByWinnerOrLoserOrderByCreatedAtDesc(participant, participant)).thenReturn(List.of());

        assertTrue(registrationService.isRegistered(11L));
        var profile = playerService.getProfile(11L);
        assertTrue(profile.removed());
        assertEquals(999L, profile.removedBy());
        assertEquals(1150, profile.rating());
        assertEquals(7, profile.gamesPlayed());
        assertNull(profile.rank());
        assertNull(playerService.getAdminPlayerInfo(11L).rank());
        assertEquals(999L, playerService.getAdminPlayerInfo(11L).removedBy());
        assertTrue(matchService.getFullMatchHistory(11L).isEmpty());
        verify(participants, never()).findLeaderboardBySeason(season);
    }

    @Test
    void removedPlayerCannotRegisterAgainOrEditProfile() {
        participant.removeFromSeason(999L);
        assertThrows(PlayerRemovedFromSeasonException.class,
                () -> registrationService.register(11L, "discord", "New name"));
        assertThrows(PlayerRemovedFromSeasonException.class,
                () -> registrationService.updateCurrentSeasonDisplayName(11L, "New name"));
        assertThrows(PlayerRemovedFromSeasonException.class, () -> playerService.requireParticipationAllowed(11L));
        assertEquals("Scorpion", participant.getDisplayName());
        verify(participants, never()).saveAndFlush(any());
        verify(players, never()).save(any());
    }

    @Test
    void removalBlocksReportsByEitherParticipantAndPendingConfirmations() {
        // A report prepared before removal must be checked again when confirmed.
        matchService.prepareMatchReport(11L, 22L, 5, 2);
        participant.removeFromSeason(999L);
        assertThrows(PlayerRemovedFromSeasonException.class,
                () -> matchService.prepareMatchReport(11L, 22L, 5, 2));
        assertThrows(PlayerRemovedFromSeasonException.class,
                () -> matchService.prepareMatchReport(22L, 11L, 5, 2));
        assertThrows(PlayerRemovedFromSeasonException.class,
                () -> matchService.confirmReportedMatch(11L, 22L, 5, 2));
        assertThrows(PlayerRemovedFromSeasonException.class,
                () -> matchService.confirmReportedMatch(22L, 11L, 5, 2));
        assertEquals(1150, participant.getRating());
        assertEquals(850, other.getRating());
        assertEquals(7, other.getGamesPlayed());
        verify(participants, never()).save(any());
        verifyNoInteractions(matches);
    }
}
