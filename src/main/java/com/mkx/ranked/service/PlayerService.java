package com.mkx.ranked.service;

import com.mkx.ranked.exception.PlayerNotFoundException;
import com.mkx.ranked.exception.PlayerNotRegisteredException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.enums.RankTier;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final SeasonPlayerRepository seasonPlayerRepository;
    private final SeasonService seasonService;

    public PlayerService(
            PlayerRepository playerRepository,
            SeasonPlayerRepository seasonPlayerRepository,
            SeasonService seasonService
    ) {
        this.playerRepository = playerRepository;
        this.seasonPlayerRepository = seasonPlayerRepository;
        this.seasonService = seasonService;
    }

    @Transactional(readOnly = true)
    public PlayerProfileDto getProfile(long discordId) {
        PlayerEntity player = findPlayerByDiscordId(discordId);
        SeasonEntity season = seasonService.getActiveSeasonEntity();
        SeasonPlayerEntity seasonPlayer = findSeasonPlayer(season, player);
        int rank = calculateRank(season, seasonPlayer);
        RankTier tier = RankTier.getTierByRank(rank);

        return new PlayerProfileDto(
                player.getId(),
                player.getDiscordId(),
                seasonPlayer.getDisplayName(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed(),
                rank,
                tier.getName(),
                tier.getEmoji(),
                seasonService.toDto(season)
        );
    }

    @Transactional(readOnly = true)
    public AdminPlayerDto getAdminPlayerInfo(long discordId) {
        PlayerEntity player = findPlayerByDiscordId(discordId);
        SeasonEntity season = seasonService.getActiveSeasonEntity();
        SeasonPlayerEntity seasonPlayer = findSeasonPlayer(season, player);
        int rank = calculateRank(season, seasonPlayer);
        RankTier tier = RankTier.getTierByRank(rank);

        return new AdminPlayerDto(
                player.getId(),
                player.getDiscordId(),
                player.getUsername(),
                seasonPlayer.getDisplayName(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed(),
                rank,
                tier.getName(),
                tier.getEmoji(),
                season.getSeasonNumber()
        );
    }

    private PlayerEntity findPlayerByDiscordId(long discordId) {
        return playerRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));
    }

    private SeasonPlayerEntity findSeasonPlayer(SeasonEntity season, PlayerEntity player) {
        return seasonPlayerRepository.findBySeasonAndPlayer(season, player)
                .orElseThrow(() -> new PlayerNotRegisteredException(player.getDiscordId()));
    }

    private int calculateRank(SeasonEntity season, SeasonPlayerEntity seasonPlayer) {
        List<SeasonPlayerEntity> standings = seasonPlayerRepository.findLeaderboardBySeason(season);
        return IntStream.range(0, standings.size())
                .filter(i -> standings.get(i).getId().equals(seasonPlayer.getId()))
                .findFirst()
                .orElseThrow(() -> new PlayerNotRegisteredException(seasonPlayer.getPlayer().getDiscordId())) + 1;
    }
}
