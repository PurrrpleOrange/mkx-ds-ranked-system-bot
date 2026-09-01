package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.PlayerNotFoundException;
import com.mkx.ranked.exception.PlayerNotRegisteredException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RegistrationService {

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
        SeasonEntity season = seasonService.getActiveSeasonEntity();
        return playerRepository.findByDiscordId(discordId)
                .map(player -> seasonPlayerRepository.existsBySeasonAndPlayer(season, player))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public RegistrationResultDto getCurrentRegistration(long discordId) {
        validateDiscordId(discordId);
        SeasonEntity season = seasonService.getActiveSeasonEntity();
        PlayerEntity player = playerRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));
        SeasonPlayerEntity seasonPlayer = seasonPlayerRepository.findBySeasonAndPlayer(season, player)
                .orElseThrow(() -> new PlayerNotRegisteredException(discordId));
        return toResult(player, seasonPlayer, season);
    }

    @Transactional
    public RegistrationResultDto register(long discordId, String discordUsername, String requestedUsername) {
        validateDiscordId(discordId);
        String displayName = normalizeGameUsername(requestedUsername);
        String currentDiscordUsername = normalizeDiscordUsername(discordUsername, displayName);
        SeasonEntity season = seasonService.getActiveSeasonEntityForReadLock();

        Optional<PlayerEntity> existingPlayer = playerRepository.findByDiscordId(discordId);
        if (existingPlayer.isPresent()
                && seasonPlayerRepository.existsBySeasonAndPlayer(season, existingPlayer.get())) {
            throw new BusinessException("You are already registered in the current season.");
        }
        if (seasonPlayerRepository.existsBySeasonAndDisplayNameIgnoreCase(season, displayName)) {
            throw new BusinessException(
                    "Username '" + displayName + "' is already registered in the current season."
            );
        }

        PlayerEntity player = existingPlayer
                .orElseGet(() -> new PlayerEntity(discordId, currentDiscordUsername));
        player.setUsername(currentDiscordUsername);
        PlayerEntity savedPlayer = playerRepository.save(player);
        SeasonPlayerEntity seasonPlayer = seasonPlayerRepository.save(
                new SeasonPlayerEntity(savedPlayer, season, displayName)
        );
        return toResult(savedPlayer, seasonPlayer, season);
    }

    private RegistrationResultDto toResult(
            PlayerEntity player,
            SeasonPlayerEntity seasonPlayer,
            SeasonEntity season
    ) {
        return new RegistrationResultDto(
                player.getId(),
                player.getDiscordId(),
                player.getUsername(),
                seasonPlayer.getDisplayName(),
                season.getId(),
                season.getSeasonNumber(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed()
        );
    }

    private String normalizeGameUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("Username must not be blank.");
        }
        return username.trim();
    }

    private String normalizeDiscordUsername(String discordUsername, String fallback) {
        return discordUsername == null || discordUsername.isBlank()
                ? fallback
                : discordUsername.trim();
    }

    private void validateDiscordId(long discordId) {
        if (discordId <= 0) {
            throw new BusinessException("Discord ID must be positive.");
        }
    }
}
