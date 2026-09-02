package com.mkx.ranked.model.dto;

public record AdminRegisteredPlayerDto(
        long seasonPlayerId,
        long discordId,
        String discordUsername,
        String displayName,
        int rating,
        int gamesPlayed
) {
}
