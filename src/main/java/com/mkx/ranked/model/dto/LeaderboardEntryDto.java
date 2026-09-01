package com.mkx.ranked.model.dto;

public record LeaderboardEntryDto(
        int rank,
        long playerId,
        Long discordId,
        String displayName,
        int rating,
        int gamesPlayed,
        String tierName,
        String tierEmoji
) {
}
