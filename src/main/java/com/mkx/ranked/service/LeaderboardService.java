package com.mkx.ranked.service;

import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.model.enums.RankTier;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class LeaderboardService {

    private final SeasonPlayerRepository seasonPlayerRepository;
    private final SeasonService seasonService;

    public LeaderboardService(
            SeasonPlayerRepository seasonPlayerRepository,
            SeasonService seasonService
    ) {
        this.seasonPlayerRepository = seasonPlayerRepository;
        this.seasonService = seasonService;
    }

    @Transactional(readOnly = true)
    public PageDto<LeaderboardEntryDto> getLeaderboardForActiveSeason(int page, int pageSize) {
        return getLeaderboardForSeason(seasonService.getActiveSeasonEntity(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getFullLeaderboardForActiveSeason() {
        return getLeaderboardForSeason(seasonService.getActiveSeasonEntity());
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getLeaderboardForSeason(long seasonId) {
        SeasonEntity season = seasonService.getSeasonEntityById(seasonId);
        return getLeaderboardForSeason(season);
    }

    private PageDto<LeaderboardEntryDto> getLeaderboardForSeason(SeasonEntity season, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), pageSize);
        Page<SeasonPlayerEntity> standings = seasonPlayerRepository.findLeaderboardBySeason(season, pageRequest);

        int offset = standings.getNumber() * standings.getSize();
        List<LeaderboardEntryDto> content = IntStream.range(0, standings.getContent().size())
                .mapToObj(i -> toLeaderboardEntry(standings.getContent().get(i), offset + i))
                .toList();

        return new PageDto<>(
                content,
                standings.getNumber(),
                standings.getTotalPages(),
                standings.getTotalElements(),
                standings.getSize()
        );
    }

    private List<LeaderboardEntryDto> getLeaderboardForSeason(SeasonEntity season) {
        List<SeasonPlayerEntity> standings = seasonPlayerRepository.findLeaderboardBySeason(season);
        return IntStream.range(0, standings.size())
                .mapToObj(i -> toLeaderboardEntry(standings.get(i), i))
                .toList();
    }

    private LeaderboardEntryDto toLeaderboardEntry(SeasonPlayerEntity seasonPlayer, int zeroBasedRank) {
        int rank = zeroBasedRank + 1;
        RankTier tier = RankTier.getTierByRank(rank);
        PlayerEntity player = seasonPlayer.getPlayer();

        return new LeaderboardEntryDto(
                rank,
                player.getId(),
                player.getDiscordId(),
                seasonPlayer.getDisplayName(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed(),
                tier.getName(),
                tier.getEmoji()
        );
    }
}
