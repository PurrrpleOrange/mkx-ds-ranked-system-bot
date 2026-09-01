package com.mkx.ranked.service;

import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.AdminPlayerDto;
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

    public AdminService(
            SeasonService seasonService,
            MatchService matchService,
            PlayerService playerService,
            LeaderboardService leaderboardService
    ) {
        this.seasonService = seasonService;
        this.matchService = matchService;
        this.playerService = playerService;
        this.leaderboardService = leaderboardService;
    }

    public SeasonDto createSeason(String name, LocalDateTime plannedEndDate) {
        return seasonService.createNewSeason(name, plannedEndDate);
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

    public AdminMatchDto getMatchInfo(long matchId) {
        return matchService.getAdminMatchInfo(matchId);
    }

    public void deleteMatch(long matchId) {
        matchService.revertMatch(matchId);
    }

    public AdminPlayerDto getPlayerInfo(long discordId) {
        return playerService.getAdminPlayerInfo(discordId);
    }

    public List<LeaderboardEntryDto> getActiveSeasonLeaderboard() {
        return leaderboardService.getFullLeaderboardForActiveSeason();
    }
}
