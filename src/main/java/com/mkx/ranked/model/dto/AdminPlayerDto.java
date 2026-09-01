package com.mkx.ranked.model.dto;

public record AdminPlayerDto(
        long playerId,
        long discordId,
        String discordUsername,
        String displayName,
        int rating,
        int gamesPlayed,
        int rank,
        String tierName,
        String tierEmoji,
        int seasonNumber
) {
}
