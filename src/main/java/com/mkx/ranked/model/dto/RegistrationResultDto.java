package com.mkx.ranked.model.dto;

public record RegistrationResultDto(
        long playerId,
        long discordId,
        String username,
        String displayName,
        long seasonId,
        int seasonNumber,
        int rating,
        int gamesPlayed
) {
}
