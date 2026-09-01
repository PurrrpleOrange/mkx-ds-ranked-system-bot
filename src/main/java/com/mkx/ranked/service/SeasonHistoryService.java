package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.SeasonNotFoundException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.dto.SeasonPlayerHistoryDto;
import com.mkx.ranked.model.enums.RankTier;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import com.mkx.ranked.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SeasonHistoryService {

    private final SeasonRepository seasonRepository;
    private final SeasonPlayerRepository seasonPlayerRepository;
    private final SeasonService seasonService;
    private final LeaderboardService leaderboardService;

    public SeasonHistoryService(
            SeasonRepository seasonRepository,
            SeasonPlayerRepository seasonPlayerRepository,
            SeasonService seasonService,
            LeaderboardService leaderboardService
    ) {
        this.seasonRepository = seasonRepository;
        this.seasonPlayerRepository = seasonPlayerRepository;
        this.seasonService = seasonService;
        this.leaderboardService = leaderboardService;
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

    private SeasonPlayerHistoryDto toHistoryDto(SeasonPlayerEntity seasonPlayer) {
        Integer finalRank = seasonPlayer.getFinalRank();
        if (finalRank == null) {
            throw new BusinessException("Finished season contains a player without final rank.");
        }
        RankTier tier = RankTier.getTierByRank(finalRank);
        PlayerEntity player = seasonPlayer.getPlayer();
        return new SeasonPlayerHistoryDto(
                player.getId(),
                player.getDiscordId(),
                seasonPlayer.getDisplayName(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed(),
                finalRank,
                tier.getName(),
                tier.getEmoji()
        );
    }
}
