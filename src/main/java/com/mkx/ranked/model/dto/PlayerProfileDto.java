package com.mkx.ranked.model.dto;

public record PlayerProfileDto(
        long playerId,
        Long discordId,
        String displayName,
        int rating,
        int gamesPlayed,
        Integer rank,
        String tierName,
        String tierEmoji,
        SeasonDto season
) {
}
