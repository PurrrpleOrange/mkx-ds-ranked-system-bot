package com.mkx.ranked.service;

import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.AdminRegisteredPlayerDto;
import com.mkx.ranked.model.dto.AdminSeasonStatisticsDto;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    private SeasonService seasonService;
    private MatchService matchService;
    private PlayerService playerService;
    private LeaderboardService leaderboardService;
    private SeasonHistoryService seasonHistoryService;
    private AdminService service;

    @BeforeEach
    void setUp() {
        seasonService = mock(SeasonService.class);
        matchService = mock(MatchService.class);
        playerService = mock(PlayerService.class);
        leaderboardService = mock(LeaderboardService.class);
        seasonHistoryService = mock(SeasonHistoryService.class);
        service = new AdminService(
                seasonService,
                matchService,
                playerService,
                leaderboardService,
                seasonHistoryService
        );
    }

    @Test
    void createsSeasonThroughSeasonService() {
        LocalDateTime plannedEnd = LocalDateTime.of(2026, 12, 1, 20, 0);
        SeasonDto expected = season(8, SeasonStatus.CREATED);
        when(seasonService.createNewSeason(8, "Winter Clash", plannedEnd)).thenReturn(expected);

        assertSame(expected, service.createSeason(8, "Winter Clash", plannedEnd));
    }

    @Test
    void activatesSeasonByNumberThroughLifecycleService() {
        SeasonDto expected = season(8, SeasonStatus.ACTIVE);
        when(seasonService.activateSeasonByNumber(8)).thenReturn(expected);

        assertSame(expected, service.activateSeason(8));
    }

    @Test
    void finishesCurrentActiveSeasonThroughLifecycleService() {
        SeasonDto expected = season(8, SeasonStatus.FINISHED);
        when(seasonService.finishActiveSeason()).thenReturn(expected);

        assertSame(expected, service.finishActiveSeason());
    }

    @Test
    void seasonInfoWithoutNumberReturnsActiveSeason() {
        SeasonDto expected = season(8, SeasonStatus.ACTIVE);
        when(seasonService.getActiveSeason()).thenReturn(expected);

        assertSame(expected, service.getSeasonInfo(null));
    }

    @Test
    void seasonInfoWithNumberReturnsThatSeason() {
        SeasonDto expected = season(7, SeasonStatus.FINISHED);
        when(seasonService.getSeasonByNumber(7)).thenReturn(expected);

        assertSame(expected, service.getSeasonInfo(7));
    }

    @Test
    void seasonInfoByIdReturnsThatSeason() {
        SeasonDto expected = season(7, SeasonStatus.FINISHED);
        when(seasonService.getSeasonById(70L)).thenReturn(expected);

        assertSame(expected, service.getSeasonInfoById(70L));
    }

    @Test
    void returnsAllSeasonsForAdminList() {
        List<SeasonDto> expected = List.of(
                season(8, SeasonStatus.ACTIVE),
                season(7, SeasonStatus.FINISHED)
        );
        when(seasonService.getAllSeasons()).thenReturn(expected);

        assertSame(expected, service.getAllSeasons());
    }

    @Test
    void updatesActiveSeasonPlannedEndThroughLifecycleService() {
        LocalDateTime plannedEnd = LocalDateTime.of(2026, 12, 15, 20, 0);
        SeasonDto expected = season(8, SeasonStatus.ACTIVE);
        when(seasonService.updatePlannedEndDate(plannedEnd)).thenReturn(expected);

        assertSame(expected, service.updateActiveSeasonPlannedEndDate(plannedEnd));
    }

    @Test
    void updatesActiveSeasonInformationThroughLifecycleService() {
        LocalDateTime plannedEnd = LocalDateTime.of(2026, 12, 15, 20, 0);
        SeasonDto expected = season(8, SeasonStatus.ACTIVE);
        when(seasonService.updateActiveSeasonInfo(4, "Winter Clash", plannedEnd)).thenReturn(expected);

        assertSame(expected, service.updateActiveSeasonInfo(4, "Winter Clash", plannedEnd));
    }

    @Test
    void returnsPreviousSeasonStatisticsById() {
        AdminSeasonStatisticsDto expected = mock(AdminSeasonStatisticsDto.class);
        when(seasonHistoryService.getFinishedSeasonStatistics(70L)).thenReturn(expected);

        assertSame(expected, service.getPreviousSeasonStatisticsById(70L));
    }

    @Test
    void returnsPreviousSeasonStatisticsByNumber() {
        AdminSeasonStatisticsDto expected = mock(AdminSeasonStatisticsDto.class);
        when(seasonHistoryService.getFinishedSeasonStatisticsByNumber(7)).thenReturn(expected);

        assertSame(expected, service.getPreviousSeasonStatisticsByNumber(7));
    }

    @Test
    void returnsAdminMatchInfoFromTransactionalMatchService() {
        AdminMatchDto expected = mock(AdminMatchDto.class);
        when(matchService.getAdminMatchInfo(501L)).thenReturn(expected);

        assertSame(expected, service.getMatchInfo(501L));
    }

    @Test
    void matchDeleteUsesExistingRollback() {
        service.deleteMatch(501L);

        verify(matchService).revertMatch(501L);
    }

    @Test
    void returnsPlayerInfoFromPlayerService() {
        AdminPlayerDto expected = mock(AdminPlayerDto.class);
        when(playerService.getAdminPlayerInfo(123L)).thenReturn(expected);

        assertSame(expected, service.getPlayerInfo(123L));
    }

    @Test
    void returnsAllRegisteredPlayersFromPlayerService() {
        List<AdminRegisteredPlayerDto> expected = List.of(mock(AdminRegisteredPlayerDto.class));
        when(playerService.getAllRegisteredPlayersForActiveSeason()).thenReturn(expected);

        assertSame(expected, service.getAllRegisteredPlayers());
    }

    @Test
    void returnsFullActiveLeaderboardForPublication() {
        List<LeaderboardEntryDto> expected = List.of(mock(LeaderboardEntryDto.class));
        when(leaderboardService.getFullLeaderboardForActiveSeason()).thenReturn(expected);

        assertSame(expected, service.getActiveSeasonLeaderboard());
    }

    private SeasonDto season(int number, SeasonStatus status) {
        return new SeasonDto(number * 10L, number, "Season " + number, status, null, null, null);
    }
}
