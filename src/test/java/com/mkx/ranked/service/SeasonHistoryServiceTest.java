package com.mkx.ranked.service;

import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.dto.SeasonPlayerHistoryDto;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeasonHistoryServiceTest {

    private SeasonRepository seasonRepository;
    private SeasonPlayerRepository seasonPlayerRepository;
    private SeasonHistoryService service;
    private SeasonEntity finishedSeason;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(SeasonRepository.class);
        seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = new SeasonService(seasonRepository, seasonPlayerRepository);
        LeaderboardService leaderboardService = mock(LeaderboardService.class);
        service = new SeasonHistoryService(
                seasonRepository,
                seasonPlayerRepository,
                seasonService,
                leaderboardService
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
}
