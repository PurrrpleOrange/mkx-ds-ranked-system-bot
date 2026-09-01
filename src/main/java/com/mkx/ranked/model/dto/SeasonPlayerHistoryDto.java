package com.mkx.ranked.model.dto;

public record SeasonPlayerHistoryDto(
        long playerId,
        Long discordId,
        String displayName,
        int rating,
        int gamesPlayed,
        int finalRank,
        String tierName,
        String tierEmoji
) {
}
