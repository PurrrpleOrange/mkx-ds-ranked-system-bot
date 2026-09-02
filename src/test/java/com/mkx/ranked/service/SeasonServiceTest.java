package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.SeasonNotFoundException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import com.mkx.ranked.repository.SeasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonServiceTest {

    private SeasonRepository seasonRepository;
    private SeasonPlayerRepository seasonPlayerRepository;
    private SeasonService service;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(SeasonRepository.class);
        seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        service = new SeasonService(seasonRepository, seasonPlayerRepository);
    }

    @Test
    void createsSeasonInCreatedStatusUsingDatabaseSequence() {
        LocalDateTime plannedEndDate = LocalDateTime.of(2026, 12, 1, 20, 0);
        when(seasonRepository.getNextSeasonNumber()).thenReturn(8L);
        when(seasonRepository.saveAndFlush(any(SeasonEntity.class))).thenAnswer(invocation -> {
            SeasonEntity season = invocation.getArgument(0);
            ReflectionTestUtils.setField(season, "id", 80L);
            return season;
        });

        SeasonDto result = service.createNewSeason("  Winter Clash  ", plannedEndDate);

        assertEquals(8, result.seasonNumber());
        assertEquals("Winter Clash", result.name());
        assertEquals(SeasonStatus.CREATED, result.status());
        assertNull(result.startDate());
        assertEquals(plannedEndDate, result.plannedEndDate());
        assertNull(result.endDate());
    }

    @Test
    void createsSeasonWithAdministratorProvidedNumber() {
        when(seasonRepository.saveAndFlush(any(SeasonEntity.class))).thenAnswer(invocation -> {
            SeasonEntity season = invocation.getArgument(0);
            ReflectionTestUtils.setField(season, "id", 1L);
            return season;
        });

        SeasonDto result = service.createNewSeason(4, "Season Four", null);

        assertEquals(4, result.seasonNumber());
        assertEquals("Season Four", result.name());
        verify(seasonRepository).existsBySeasonNumber(4);
    }

    @Test
    void refusesInvalidOrDuplicateAdministratorProvidedNumber() {
        when(seasonRepository.existsBySeasonNumber(4)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.createNewSeason(0, "Season", null));
        assertThrows(BusinessException.class, () -> service.createNewSeason(4, "Season", null));

        verify(seasonRepository, never()).saveAndFlush(any());
    }

    @Test
    void activatesCreatedSeason() {
        SeasonEntity season = season(1L, 1, SeasonStatus.CREATED);
        when(seasonRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(season));
        when(seasonRepository.countByStatus(SeasonStatus.ACTIVE)).thenReturn(0L);
        when(seasonRepository.saveAndFlush(season)).thenReturn(season);

        SeasonDto result = service.activateSeason(1L);

        assertEquals(SeasonStatus.ACTIVE, result.status());
        assertNotNull(result.startDate());
        assertNull(result.endDate());
    }

    @Test
    void refusesToFinishCreatedSeason() {
        SeasonEntity season = season(1L, 1, SeasonStatus.CREATED);
        when(seasonRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(season));

        assertThrows(BusinessException.class, () -> service.finishSeason(1L));

        verify(seasonPlayerRepository, never()).findLeaderboardBySeason(any());
    }

    @Test
    void refusesToActivateFinishedSeason() {
        SeasonEntity season = season(1L, 1, SeasonStatus.FINISHED);
        when(seasonRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(season));

        assertThrows(BusinessException.class, () -> service.activateSeason(1L));

        verify(seasonRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToActivateAlreadyActiveSeason() {
        SeasonEntity season = season(1L, 1, SeasonStatus.ACTIVE);
        when(seasonRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(season));

        assertThrows(BusinessException.class, () -> service.activateSeason(1L));

        verify(seasonRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToActivateSecondSeason() {
        SeasonEntity season = season(2L, 2, SeasonStatus.CREATED);
        when(seasonRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(season));
        when(seasonRepository.countByStatus(SeasonStatus.ACTIVE)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.activateSeason(2L));

        assertEquals(SeasonStatus.CREATED, season.getStatus());
        verify(seasonRepository, never()).saveAndFlush(any());
    }

    @Test
    void finishPersistsDeterministicFinalRanksAndEndDate() {
        SeasonEntity season = season(1L, 1, SeasonStatus.ACTIVE);
        SeasonPlayerEntity first = seasonPlayer(11L, 101L, season, 1200, 10);
        SeasonPlayerEntity second = seasonPlayer(12L, 102L, season, 1200, 8);
        SeasonPlayerEntity third = seasonPlayer(13L, 103L, season, 1100, 20);
        when(seasonRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(season));
        when(seasonPlayerRepository.findLeaderboardBySeason(season))
                .thenReturn(List.of(first, second, third));
        when(seasonRepository.save(season)).thenReturn(season);

        SeasonDto result = service.finishSeason(1L);

        assertEquals(1, first.getFinalRank());
        assertEquals(2, second.getFinalRank());
        assertEquals(3, third.getFinalRank());
        assertEquals(SeasonStatus.FINISHED, result.status());
        assertNotNull(result.endDate());
        verify(seasonPlayerRepository).saveAll(List.of(first, second, third));
    }

    @Test
    void refusesToFinishSeasonTwice() {
        SeasonEntity season = season(1L, 1, SeasonStatus.FINISHED);
        when(seasonRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(season));

        assertThrows(BusinessException.class, () -> service.finishSeason(1L));
    }

    @Test
    void returnsAllSeasonsNewestFirstWithInternalIds() {
        SeasonEntity newest = season(90L, 9, SeasonStatus.ACTIVE);
        SeasonEntity older = season(80L, 8, SeasonStatus.FINISHED);
        when(seasonRepository.findAllByOrderBySeasonNumberDesc()).thenReturn(List.of(newest, older));

        List<SeasonDto> result = service.getAllSeasons();

        assertEquals(List.of(9, 8), result.stream().map(SeasonDto::seasonNumber).toList());
        assertEquals(List.of(90L, 80L), result.stream().map(SeasonDto::id).toList());
    }

    @Test
    void adminLifecycleLookupRejectsUnknownSeason() {
        when(seasonRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThrows(SeasonNotFoundException.class, () -> service.activateSeason(999L));
        assertThrows(SeasonNotFoundException.class, () -> service.finishSeason(999L));
    }

    @Test
    void updatesPlannedEndDateOnlyOnLockedActiveSeason() {
        SeasonEntity active = season(90L, 9, SeasonStatus.ACTIVE);
        LocalDateTime plannedEnd = LocalDateTime.of(2026, 12, 15, 20, 0);
        when(seasonRepository.findByStatusForUpdate(SeasonStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(seasonRepository.save(active)).thenReturn(active);

        SeasonDto result = service.updatePlannedEndDate(plannedEnd);

        assertEquals(plannedEnd, result.plannedEndDate());
        verify(seasonRepository).findByStatusForUpdate(SeasonStatus.ACTIVE);
        verify(seasonRepository).save(active);
    }

    @Test
    void updatesActiveSeasonNumberNameAndPlannedEndDateUnderOneLock() {
        SeasonEntity active = season(90L, 9, SeasonStatus.ACTIVE);
        LocalDateTime plannedEnd = LocalDateTime.of(2026, 12, 15, 20, 0);
        when(seasonRepository.findByStatusForUpdate(SeasonStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(seasonRepository.saveAndFlush(active)).thenReturn(active);

        SeasonDto result = service.updateActiveSeasonInfo(4, "  Winter Clash  ", plannedEnd);

        assertEquals(4, result.seasonNumber());
        assertEquals("Winter Clash", result.name());
        assertEquals(plannedEnd, result.plannedEndDate());
        verify(seasonRepository).findByStatusForUpdate(SeasonStatus.ACTIVE);
        verify(seasonRepository).existsBySeasonNumberAndIdNot(4, 90L);
        verify(seasonRepository).saveAndFlush(active);
    }

    @Test
    void activeSeasonInformationRejectsInvalidNumberOrNameBeforeLocking() {
        assertThrows(BusinessException.class, () -> service.updateActiveSeasonInfo(0, "Season", null));
        assertThrows(BusinessException.class, () -> service.updateActiveSeasonInfo(4, "   ", null));
        assertThrows(BusinessException.class, () -> service.updateActiveSeasonInfo(4, "x".repeat(101), null));

        verify(seasonRepository, never()).findByStatusForUpdate(any());
    }

    @Test
    void activeSeasonNumberMustBeUnique() {
        SeasonEntity active = season(90L, 9, SeasonStatus.ACTIVE);
        when(seasonRepository.findByStatusForUpdate(SeasonStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(seasonRepository.existsBySeasonNumberAndIdNot(4, 90L)).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> service.updateActiveSeasonInfo(4, "Season Four", null)
        );

        assertEquals(9, active.getSeasonNumber());
        verify(seasonRepository, never()).saveAndFlush(any());
    }

    private SeasonEntity season(long id, int number, SeasonStatus status) {
        SeasonEntity season = new SeasonEntity(number, "Season " + number, null);
        ReflectionTestUtils.setField(season, "id", id);
        season.setStatus(status);
        return season;
    }

    private SeasonPlayerEntity seasonPlayer(
            long seasonPlayerId,
            long playerId,
            SeasonEntity season,
            int rating,
            int gamesPlayed
    ) {
        PlayerEntity player = new PlayerEntity(playerId + 1000, "player-" + playerId);
        ReflectionTestUtils.setField(player, "id", playerId);
        SeasonPlayerEntity seasonPlayer = new SeasonPlayerEntity(player, season, "Player " + playerId);
        ReflectionTestUtils.setField(seasonPlayer, "id", seasonPlayerId);
        seasonPlayer.setRating(rating);
        seasonPlayer.setGamesPlayed(gamesPlayed);
        return seasonPlayer;
    }
}
