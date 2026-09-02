package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.SeasonNotFoundException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.AdminSeasonStatisticsDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.dto.SeasonPlayerHistoryDto;
import com.mkx.ranked.model.enums.RankTier;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import com.mkx.ranked.repository.SeasonRepository;
import com.mkx.ranked.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SeasonHistoryService {

    private static final String UNRANKED_TIER_NAME = "Без ранга";
    private static final String UNRANKED_TIER_EMOJI = "⚪";

    private final SeasonRepository seasonRepository;
    private final SeasonPlayerRepository seasonPlayerRepository;
    private final SeasonService seasonService;
    private final LeaderboardService leaderboardService;
    private final MatchRepository matchRepository;

    public SeasonHistoryService(
            SeasonRepository seasonRepository,
            SeasonPlayerRepository seasonPlayerRepository,
            SeasonService seasonService,
            LeaderboardService leaderboardService,
            MatchRepository matchRepository
    ) {
        this.seasonRepository = seasonRepository;
        this.seasonPlayerRepository = seasonPlayerRepository;
        this.seasonService = seasonService;
        this.leaderboardService = leaderboardService;
        this.matchRepository = matchRepository;
    }

    @Transactional(readOnly = true)
    public List<SeasonDto> getFinishedSeasons() {
        return seasonRepository.findAllByStatusOrderByEndDateDescSeasonNumberDesc(SeasonStatus.FINISHED)
                .stream()
                .map(seasonService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeasonDto getFinishedSeason(long seasonId) {
        return seasonService.toDto(getFinishedSeasonEntity(seasonId));
    }

    @Transactional(readOnly = true)
    public SeasonDto getFinishedSeasonByNumber(int seasonNumber) {
        SeasonEntity season = seasonRepository.findBySeasonNumber(seasonNumber)
                .orElseThrow(() -> new SeasonNotFoundException(seasonNumber));
        validateFinished(season);
        return seasonService.toDto(season);
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getFinishedSeasonLeaderboard(long seasonId) {
        getFinishedSeasonEntity(seasonId);
        return leaderboardService.getLeaderboardForSeason(seasonId);
    }

    @Transactional(readOnly = true)
    public AdminSeasonStatisticsDto getFinishedSeasonStatistics(long seasonId) {
        return toStatistics(getFinishedSeasonEntity(seasonId));
    }

    @Transactional(readOnly = true)
    public AdminSeasonStatisticsDto getFinishedSeasonStatisticsByNumber(int seasonNumber) {
        SeasonEntity season = seasonRepository.findBySeasonNumber(seasonNumber)
                .orElseThrow(() -> new SeasonNotFoundException(seasonNumber));
        validateFinished(season);
        return toStatistics(season);
    }

    @Transactional(readOnly = true)
    public Optional<SeasonPlayerHistoryDto> findPlayerInFinishedSeason(long seasonId, long discordId) {
        SeasonEntity season = getFinishedSeasonEntity(seasonId);
        return seasonPlayerRepository.findBySeasonAndPlayerDiscordId(season, discordId)
                .map(this::toHistoryDto);
    }

    private SeasonEntity getFinishedSeasonEntity(long seasonId) {
        SeasonEntity season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new SeasonNotFoundException(seasonId));
        validateFinished(season);
        return season;
    }

    private void validateFinished(SeasonEntity season) {
        if (season.getStatus() != SeasonStatus.FINISHED) {
            throw new BusinessException("Season history is available only for FINISHED seasons.");
        }
    }

    private AdminSeasonStatisticsDto toStatistics(SeasonEntity season) {
        List<LeaderboardEntryDto> leaderboard = leaderboardService.getLeaderboardForSeason(season.getId());
        int averageRating = leaderboard.isEmpty()
                ? 0
                : (int) Math.round(leaderboard.stream()
                        .mapToInt(LeaderboardEntryDto::rating)
                        .average()
                        .orElse(0));

        return new AdminSeasonStatisticsDto(
                seasonService.toDto(season),
                seasonPlayerRepository.countBySeason(season),
                matchRepository.countBySeason(season),
                averageRating,
                leaderboard.stream().limit(10).toList()
        );
    }

    private SeasonPlayerHistoryDto toHistoryDto(SeasonPlayerEntity seasonPlayer) {
        Integer finalRank = seasonPlayer.getFinalRank();
        RankTier tier = finalRank == null ? null : RankTier.getTierByRank(finalRank);
        PlayerEntity player = seasonPlayer.getPlayer();
        return new SeasonPlayerHistoryDto(
                player.getId(),
                player.getDiscordId(),
                seasonPlayer.getDisplayName(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed(),
                finalRank,
                tier == null ? UNRANKED_TIER_NAME : tier.getName(),
                tier == null ? UNRANKED_TIER_EMOJI : tier.getEmoji()
        );
    }
}
