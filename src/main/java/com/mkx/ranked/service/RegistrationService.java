package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.PlayerNotFoundException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.RegistrationProfileDto;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import com.mkx.ranked.model.dto.RegistrationReviewDto;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RegistrationService {

    private static final int DEFAULT_RATING = 1000;

    private final PlayerRepository playerRepository;
    private final SeasonPlayerRepository seasonPlayerRepository;
    private final SeasonService seasonService;

    public RegistrationService(
            PlayerRepository playerRepository,
            SeasonPlayerRepository seasonPlayerRepository,
            SeasonService seasonService
    ) {
        this.playerRepository = playerRepository;
        this.seasonPlayerRepository = seasonPlayerRepository;
        this.seasonService = seasonService;
    }

    @Transactional(readOnly = true)
    public boolean isRegistered(long discordId) {
        validateDiscordId(discordId);
        return playerRepository.existsByDiscordId(discordId);
    }

    @Transactional(readOnly = true)
    public RegistrationResultDto getCurrentRegistration(long discordId) {
        validateDiscordId(discordId);
        PlayerEntity player = playerRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));
        SeasonEntity season = seasonService.getActiveSeasonEntity();
        SeasonPlayerEntity seasonPlayer = seasonPlayerRepository.findBySeasonAndPlayer(season, player)
                .orElseThrow(() -> new BusinessException("Player is linked to Discord but not registered in active season."));
        return toResult(player, seasonPlayer, season);
    }

    @Transactional(readOnly = true)
    public RegistrationReviewDto reviewRegistration(String requestedUsername) {
        String username = normalizeUsername(requestedUsername);
        Optional<PlayerEntity> player = playerRepository.findFirstByUsernameIgnoreCase(username);

        Optional<RegistrationProfileDto> candidate = player
                .filter(it -> it.getDiscordId() == null)
                .map(this::toRegistrationProfile);

        return new RegistrationReviewDto(username, candidate);
    }

    @Transactional(readOnly = true)
    public boolean isClaimedUsername(String requestedUsername) {
        String username = normalizeUsername(requestedUsername);
        return playerRepository.findFirstByUsernameIgnoreCase(username)
                .map(PlayerEntity::getDiscordId)
                .isPresent();
    }

    @Transactional
    public RegistrationResultDto register(long discordId, String discordUsername, String requestedUsername) {
        validateDiscordId(discordId);
        String username = normalizeUsername(requestedUsername);

        PlayerEntity player = playerRepository.findFirstByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BusinessException("Player with username '" + username + "' was not found."));

        validateDiscordBinding(discordId, player);

        player.setDiscordId(discordId);

        SeasonEntity season = seasonService.getActiveSeasonEntity();
        SeasonPlayerEntity seasonPlayer = seasonPlayerRepository.findBySeasonAndPlayer(season, player)
                .orElseGet(() -> seasonPlayerRepository.save(new SeasonPlayerEntity(player, season)));

        PlayerEntity savedPlayer = playerRepository.save(player);
        return toResult(savedPlayer, seasonPlayer, season);
    }

    @Transactional
    public RegistrationResultDto claimProfile(long discordId, String discordUsername, long playerId) {
        validateDiscordId(discordId);
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new BusinessException("Player profile #" + playerId + " was not found."));

        validateDiscordBinding(discordId, player);

        player.setDiscordId(discordId);
        SeasonEntity season = seasonService.getActiveSeasonEntity();
        SeasonPlayerEntity seasonPlayer = seasonPlayerRepository.findBySeasonAndPlayer(season, player)
                .orElseGet(() -> seasonPlayerRepository.save(new SeasonPlayerEntity(player, season)));

        PlayerEntity savedPlayer = playerRepository.save(player);
        return toResult(savedPlayer, seasonPlayer, season);
    }

    private void validateDiscordBinding(long discordId, PlayerEntity player) {
        if (player.getDiscordId() != null && !player.getDiscordId().equals(discordId)) {
            throw new BusinessException("Player profile is already linked to another Discord account.");
        }

        playerRepository.findByDiscordId(discordId)
                .filter(existing -> !existing.getId().equals(player.getId()))
                .ifPresent(existing -> {
                    throw new BusinessException("This Discord account is already linked to another player.");
                });
    }

    private RegistrationProfileDto toRegistrationProfile(PlayerEntity player) {
        Optional<SeasonPlayerEntity> latestSeasonPlayer =
                seasonPlayerRepository.findAllByPlayerOrderBySeason_SeasonNumberDesc(player)
                        .stream()
                        .findFirst();

        return new RegistrationProfileDto(
                player.getId(),
                player.getDisplayName(),
                latestSeasonPlayer.map(SeasonPlayerEntity::getRating).orElse(DEFAULT_RATING),
                latestSeasonPlayer.map(SeasonPlayerEntity::getGamesPlayed).orElse(0)
        );
    }

    private RegistrationResultDto toResult(PlayerEntity player, SeasonPlayerEntity seasonPlayer, SeasonEntity season) {
        return new RegistrationResultDto(
                player.getId(),
                player.getDiscordId(),
                player.getUsername(),
                player.getDisplayName(),
                season.getId(),
                season.getSeasonNumber(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed()
        );
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("Username must not be blank.");
        }
        return username.trim();
    }

    private void validateDiscordId(long discordId) {
        if (discordId <= 0) {
            throw new BusinessException("Discord ID must be positive.");
        }
    }
}
