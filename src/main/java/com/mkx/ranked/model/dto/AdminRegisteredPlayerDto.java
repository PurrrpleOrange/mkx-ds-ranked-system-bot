package com.mkx.ranked.model.dto;

public record AdminRegisteredPlayerDto(
        long playerId,
        long discordId,
        String discordUsername,
        String displayName,
        int rating,
        int gamesPlayed
) {
}
