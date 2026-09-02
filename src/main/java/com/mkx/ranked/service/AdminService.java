package com.mkx.ranked.service;

import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.AdminRegisteredPlayerDto;
import com.mkx.ranked.model.dto.AdminSeasonStatisticsDto;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.SeasonDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    private final SeasonService seasonService;
    private final MatchService matchService;
    private final PlayerService playerService;
    private final LeaderboardService leaderboardService;
    private final SeasonHistoryService seasonHistoryService;

    public AdminService(
            SeasonService seasonService,
            MatchService matchService,
            PlayerService playerService,
            LeaderboardService leaderboardService,
            SeasonHistoryService seasonHistoryService
    ) {
        this.seasonService = seasonService;
        this.matchService = matchService;
        this.playerService = playerService;
        this.leaderboardService = leaderboardService;
        this.seasonHistoryService = seasonHistoryService;
    }

    public SeasonDto createSeason(int seasonNumber, String name, LocalDateTime plannedEndDate) {
        return seasonService.createNewSeason(seasonNumber, name, plannedEndDate);
    }

    public SeasonDto activateSeason(int seasonNumber) {
        return seasonService.activateSeasonByNumber(seasonNumber);
    }

    public SeasonDto finishActiveSeason() {
        return seasonService.finishActiveSeason();
    }

    public SeasonDto getSeasonInfo(Integer seasonNumber) {
        return seasonNumber == null
                ? seasonService.getActiveSeason()
                : seasonService.getSeasonByNumber(seasonNumber);
    }

    public SeasonDto getSeasonInfoById(long seasonId) {
        return seasonService.getSeasonById(seasonId);
    }

    public List<SeasonDto> getAllSeasons() {
        return seasonService.getAllSeasons();
    }

    public SeasonDto updateActiveSeasonPlannedEndDate(LocalDateTime plannedEndDate) {
        return seasonService.updatePlannedEndDate(plannedEndDate);
    }

    public SeasonDto updateActiveSeasonInfo(
            int seasonNumber,
            String name,
            LocalDateTime plannedEndDate
    ) {
        return seasonService.updateActiveSeasonInfo(seasonNumber, name, plannedEndDate);
    }

    public AdminSeasonStatisticsDto getPreviousSeasonStatisticsById(long seasonId) {
        return seasonHistoryService.getFinishedSeasonStatistics(seasonId);
    }

    public AdminSeasonStatisticsDto getPreviousSeasonStatisticsByNumber(int seasonNumber) {
        return seasonHistoryService.getFinishedSeasonStatisticsByNumber(seasonNumber);
    }

    public AdminMatchDto getMatchInfo(long matchId) {
        return matchService.getAdminMatchInfo(matchId);
    }

    public void deleteMatch(long matchId) {
        matchService.revertMatch(matchId);
    }

    public AdminPlayerDto getPlayerInfo(long discordId) {
        return playerService.getAdminPlayerInfo(discordId);
    }

    public List<AdminRegisteredPlayerDto> getAllRegisteredPlayers() {
        return playerService.getAllRegisteredPlayersForActiveSeason();
    }

    public List<LeaderboardEntryDto> getActiveSeasonLeaderboard() {
        return leaderboardService.getFullLeaderboardForActiveSeason();
    }
}
